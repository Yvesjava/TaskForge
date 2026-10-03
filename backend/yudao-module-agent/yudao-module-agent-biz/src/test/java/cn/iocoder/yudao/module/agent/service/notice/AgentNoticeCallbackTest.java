package cn.iocoder.yudao.module.agent.service.notice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.agent.controller.admin.notice.AgentNoticeCallbackController;
import cn.iocoder.yudao.module.agent.controller.admin.notice.vo.AgentNoticeCallbackReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.notice.vo.AgentNoticeCallbackRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskOperationRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookCallbackToken;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookProperties;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookSignature;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookVerifyResult;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-NOTICE-04 卡片验收/打回回调与幂等测试。
 *
 * <p>覆盖回调令牌绑定、控制面动作复用、重复回调幂等，以及控制器对签名与可重试
 * 结果的 HTTP 状态映射。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentNoticeCallbackTest {

    private static final String SECRET = "taskforge-callback-secret";
    private static final Long TASK_ID = 9012L;
    private static final String TASK_NO = "TASK-20261003-001";
    private static final String REPORT_VERSION = "v3";

    @Mock
    private AgentTaskService taskService;

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private WebhookSignature webhookSignature;

    private WebhookCallbackToken realToken;
    private AgentNoticeCallbackServiceImpl service;
    private AgentNoticeCallbackController controller;

    @BeforeEach
    void setUp() {
        WebhookProperties properties = new WebhookProperties();
        properties.setSecret(SECRET);
        realToken = new WebhookCallbackToken(properties);

        service = new AgentNoticeCallbackServiceImpl();
        ReflectionTestUtils.setField(service, "callbackToken", realToken);
        ReflectionTestUtils.setField(service, "taskService", taskService);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);

        controller = new AgentNoticeCallbackController();
        ReflectionTestUtils.setField(controller, "webhookSignature", webhookSignature);
        ReflectionTestUtils.setField(controller, "callbackService", service);
    }

    @Test
    void token_isDeterministicAndBindsAction() {
        String first = realToken.issue(TASK_NO, "accept", REPORT_VERSION);
        String second = realToken.issue(TASK_NO, "accept", REPORT_VERSION);

        assertThat(first).isEqualTo(second);
        assertThat(realToken.matches(first, TASK_NO, "accept", REPORT_VERSION)).isTrue();
    }

    @Test
    void token_rejectsDifferentTaskActionOrVersion() {
        String token = realToken.issue(TASK_NO, "accept", REPORT_VERSION);

        assertThat(realToken.matches(token, TASK_NO, "reject", REPORT_VERSION)).isFalse();
        assertThat(realToken.matches(token, "TASK-20261003-002", "accept", REPORT_VERSION)).isFalse();
        assertThat(realToken.matches(token, TASK_NO, "accept", "v4")).isFalse();
    }

    @Test
    void handle_rejectsInvalidTokenWithoutDelegating() {
        AgentNoticeCallbackReqVO request = request("accept", null);
        request.setToken("deadbeef");

        AgentNoticeCallbackRespVO response = service.handle(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isRetryable()).isFalse();
        verify(taskMapper, never()).selectByTaskNo(any());
        verify(taskService, never()).accept(anyLong(), any());
        verify(taskService, never()).reject(anyLong(), any(), any());
    }

    @Test
    void handle_acceptDelegatesToControlPlaneWithResolvedIdAndTokenKey() {
        AgentNoticeCallbackReqVO request = request("accept", null);
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(task("WAITING_ACCEPTANCE"));
        when(taskService.accept(TASK_ID, request.getToken())).thenReturn(operation("ACCEPTED", "op_200"));

        AgentNoticeCallbackRespVO response = service.handle(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isRetryable()).isFalse();
        assertThat(response.getTaskNo()).isEqualTo(TASK_NO);
        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getOperationId()).isEqualTo("op_200");
        verify(taskService).accept(TASK_ID, request.getToken());
    }

    @Test
    void handle_rejectDelegatesWithFeedback() {
        AgentNoticeCallbackReqVO request = request("reject", "样式问题");
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(task("WAITING_ACCEPTANCE"));
        when(taskService.reject(TASK_ID, "样式问题", request.getToken()))
                .thenReturn(operation("REJECTED", "op_201"));

        AgentNoticeCallbackRespVO response = service.handle(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getOperationId()).isEqualTo("op_201");
        verify(taskService).reject(TASK_ID, "样式问题", request.getToken());
    }

    @Test
    void handle_alreadyTerminalTask_returnsCurrentResultWithoutTransition() {
        AgentNoticeCallbackReqVO request = request("accept", null);
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(task("ACCEPTED"));

        AgentNoticeCallbackRespVO response = service.handle(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getOperationId()).isNull();
        verify(taskService, never()).accept(anyLong(), any());
        verify(taskService, never()).reject(anyLong(), any(), any());
    }

    @Test
    void handle_transitionConflict_returnsCurrentResult() {
        AgentNoticeCallbackReqVO request = request("accept", null);
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(task("WAITING_ACCEPTANCE"));
        when(taskService.accept(TASK_ID, request.getToken()))
                .thenThrow(new ServiceException(ErrorCodeConstants.TASK_STATUS_UPDATE_CONFLICT));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED"));

        AgentNoticeCallbackRespVO response = service.handle(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getOperationId()).isNull();
    }

    @Test
    void handle_taskNotFound_returnsPermanentFailure() {
        AgentNoticeCallbackReqVO request = request("accept", null);
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(null);

        AgentNoticeCallbackRespVO response = service.handle(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isRetryable()).isFalse();
        verify(taskService, never()).accept(anyLong(), any());
    }

    @Test
    void handle_rejectMissingFeedback_returnsPermanentFailure() {
        AgentNoticeCallbackReqVO request = request("reject", " ");
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(task("WAITING_ACCEPTANCE"));
        when(taskService.reject(TASK_ID, " ", request.getToken()))
                .thenThrow(new ServiceException(ErrorCodeConstants.TASK_REJECT_FEEDBACK_REQUIRED));

        AgentNoticeCallbackRespVO response = service.handle(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isRetryable()).isFalse();
    }

    @Test
    void handle_unexpectedException_returnsRetryableFailure() {
        AgentNoticeCallbackReqVO request = request("accept", null);
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(task("WAITING_ACCEPTANCE"));
        when(taskService.accept(TASK_ID, request.getToken())).thenThrow(new RuntimeException("db down"));

        AgentNoticeCallbackRespVO response = service.handle(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isRetryable()).isTrue();
    }

    @Test
    void controller_rejectsInvalidSignatureWith401() {
        when(webhookSignature.verify(any(), any(), any(), any()))
                .thenReturn(WebhookVerifyResult.INVALID_SIGNATURE);

        ResponseEntity<AgentNoticeCallbackRespVO> response =
                controller.callback("1", "nonce", "signature", "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().isRetryable()).isFalse();
        verify(taskMapper, never()).selectByTaskNo(any());
    }

    @Test
    void controller_delegatesValidCallbackAndReturns200() {
        AgentNoticeCallbackReqVO request = request("accept", null);
        when(webhookSignature.verify(any(), any(), any(), any())).thenReturn(WebhookVerifyResult.VALID);
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(task("WAITING_ACCEPTANCE"));
        when(taskService.accept(TASK_ID, request.getToken())).thenReturn(operation("ACCEPTED", "op_200"));

        ResponseEntity<AgentNoticeCallbackRespVO> response =
                controller.callback("1", "nonce", "signature", JsonUtils.toJsonString(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void controller_retryableFailure_returns500() {
        AgentNoticeCallbackReqVO request = request("accept", null);
        when(webhookSignature.verify(any(), any(), any(), any())).thenReturn(WebhookVerifyResult.VALID);
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(task("WAITING_ACCEPTANCE"));
        when(taskService.accept(TASK_ID, request.getToken())).thenThrow(new RuntimeException("db down"));

        ResponseEntity<AgentNoticeCallbackRespVO> response =
                controller.callback("1", "nonce", "signature", JsonUtils.toJsonString(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isRetryable()).isTrue();
    }

    @Test
    void controller_invalidJson_returns400() {
        when(webhookSignature.verify(any(), any(), any(), any())).thenReturn(WebhookVerifyResult.VALID);

        ResponseEntity<AgentNoticeCallbackRespVO> response =
                controller.callback("1", "nonce", "signature", "not-json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().isRetryable()).isFalse();
    }

    private AgentNoticeCallbackReqVO request(String action, String feedback) {
        AgentNoticeCallbackReqVO request = new AgentNoticeCallbackReqVO();
        request.setTaskNo(TASK_NO);
        request.setAction(action);
        request.setReportVersion(REPORT_VERSION);
        request.setFeedback(feedback);
        request.setToken(realToken.issue(TASK_NO, action, REPORT_VERSION));
        return request;
    }

    private static AgentTaskDO task(String status) {
        return AgentTaskDO.builder()
                .id(TASK_ID)
                .taskNo(TASK_NO)
                .status(status)
                .docVersion(5)
                .executionGeneration(0L)
                .build();
    }

    private static AgentTaskOperationRespVO operation(String status, String operationId) {
        AgentTaskOperationRespVO response = new AgentTaskOperationRespVO();
        response.setTaskId(TASK_ID);
        response.setTaskNo(TASK_NO);
        response.setStatus(status);
        response.setDocVersion(5);
        response.setExecutionGeneration(0L);
        response.setOperationId(operationId);
        return response;
    }

}
