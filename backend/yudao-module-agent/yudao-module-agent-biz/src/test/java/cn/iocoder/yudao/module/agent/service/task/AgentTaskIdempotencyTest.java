package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskOperationRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskSubmitRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskUpdateDocumentReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskUpdateDocumentRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocument;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocumentParser;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocumentSectionValidator;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocumentValidator;
import cn.iocoder.yudao.module.agent.service.doc.TaskFrontMatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-TASK-05 请求幂等键与统一错误响应的专项单元测试。
 *
 * <p>覆盖投递、编辑与生命周期动作的幂等重放（重复请求不产生重复副作用），
 * 并验证错误与审计记录不会泄露文档中可能携带的凭证。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskIdempotencyTest {

    private static final Long TASK_ID = 9012L;
    private static final String TASK_NO = "TASK-20261001-088";
    private static final String IDEMPOTENCY_KEY = "req_20261003_0001";

    @Mock
    private TaskDocumentParser documentParser;

    @Mock
    private TaskDocumentValidator documentValidator;

    @Mock
    private TaskDocumentSectionValidator sectionValidator;

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskOperationLogMapper operationLogMapper;

    @Mock
    private AgentTaskStateMachine stateMachine;

    @InjectMocks
    private AgentTaskServiceImpl taskService;

    @Test
    void submit_replayedIdempotencyKey_returnsFirstResultWithoutSecondInsert() {
        when(operationLogMapper.selectByRequestKey(IDEMPOTENCY_KEY))
                .thenReturn(submitLog(77L));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("PENDING"));

        AgentTaskSubmitRespVO response = taskService.submit(docText(), IDEMPOTENCY_KEY);

        assertThat(response.getTaskId()).isEqualTo(TASK_ID);
        assertThat(response.getTaskNo()).isEqualTo(TASK_NO);
        assertThat(response.getOperationId()).isEqualTo("op_77");

        verify(documentParser, never()).parse(anyString());
        verify(taskMapper, never()).insert(any(AgentTaskDO.class));
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    @Test
    void submit_concurrentDuplicateIdempotencyKey_rollsBackLoserAndReturnsWinner() {
        when(documentParser.parse(anyString())).thenReturn(validTaskDocument());
        when(operationLogMapper.selectByRequestKey(IDEMPOTENCY_KEY))
                .thenReturn(null, submitLog(77L));
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(null);
        doAnswer(invocation -> {
            AgentTaskDO task = invocation.getArgument(0);
            task.setId(TASK_ID);
            return 1;
        }).when(taskMapper).insert(any(AgentTaskDO.class));
        when(operationLogMapper.insert(any(AgentTaskOperationLogDO.class)))
                .thenThrow(new DuplicateKeyException("duplicate request idempotency key"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("PENDING"));

        AgentTaskSubmitRespVO response = taskService.submit(docText(), IDEMPOTENCY_KEY);

        assertThat(response.getTaskId()).isEqualTo(TASK_ID);
        assertThat(response.getOperationId()).isEqualTo("op_77");
        verify(taskMapper).deleteById(TASK_ID);
    }

    @ParameterizedTest
    @EnumSource(value = AgentTaskAction.class,
            names = {"PAUSE", "RESUME", "CANCEL", "RE_ENQUEUE", "DELETE"})
    void lifecycle_replayedIdempotencyKey_returnsFirstResultWithoutSecondTransition(AgentTaskAction action) {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY))
                .thenReturn(lifecycleLog(88L, action));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("PAUSED"));

        AgentTaskOperationRespVO response = invokeLifecycle(action);

        assertThat(response.getOperationId()).isEqualTo("op_88");
        assertThat(response.getStatus()).isEqualTo("PAUSED");
        verify(stateMachine, never()).transition(any(AgentTaskTransitionCommand.class));
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    @Test
    void updateDocument_replayedIdempotencyKey_returnsFirstResultWithoutSecondUpdate() {
        AgentTaskOperationLogDO existingLog = AgentTaskOperationLogDO.builder()
                .id(88L).taskId(TASK_ID).action("EDIT").docVersion(2)
                .requestIdempotencyKey(IDEMPOTENCY_KEY).build();
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY))
                .thenReturn(existingLog);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pausedTask(2));

        AgentTaskUpdateDocumentRespVO response = taskService.updateDocument(
                TASK_ID, updateReq(2, "edited doc"), IDEMPOTENCY_KEY, "2");

        assertThat(response.getDocVersion()).isEqualTo(2);
        assertThat(response.getOperationId()).isEqualTo("op_88");
        verify(taskMapper, never()).updateDocumentIfVersionMatches(anyLong(), any(), anyString(), any(), any(), any());
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    @Test
    void submit_duplicateTaskNo_errorDoesNotLeakDocumentCredentials() {
        String credential = "supersecret-credential-token";
        String document = docTextWithCredential(credential);
        when(documentParser.parse(document)).thenReturn(validTaskDocument());
        when(operationLogMapper.selectByRequestKey(IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectByTaskNo(TASK_NO))
                .thenReturn(AgentTaskDO.builder().id(9001L).taskNo(TASK_NO).build());

        assertThatThrownBy(() -> taskService.submit(document, IDEMPOTENCY_KEY))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException serviceException = (ServiceException) ex;
                    assertThat(serviceException.getCode())
                            .isEqualTo(ErrorCodeConstants.TASK_SUBMIT_TASK_NO_DUPLICATE.getCode());
                    assertThat(serviceException.getMessage()).doesNotContain(credential);
                });
    }

    @Test
    void submit_success_auditDoesNotPersistRawDocumentPayload() {
        String document = docTextWithCredential("supersecret-credential-token");
        when(documentParser.parse(document)).thenReturn(validTaskDocument());
        when(operationLogMapper.selectByRequestKey(IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(null);
        doAnswer(invocation -> {
            AgentTaskDO task = invocation.getArgument(0);
            task.setId(TASK_ID);
            return 1;
        }).when(taskMapper).insert(any(AgentTaskDO.class));
        doAnswer(invocation -> {
            AgentTaskOperationLogDO log = invocation.getArgument(0);
            log.setId(77L);
            return 1;
        }).when(operationLogMapper).insert(any(AgentTaskOperationLogDO.class));

        taskService.submit(document, IDEMPOTENCY_KEY);

        ArgumentCaptor<AgentTaskOperationLogDO> logCaptor = ArgumentCaptor.forClass(AgentTaskOperationLogDO.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        AgentTaskOperationLogDO persisted = logCaptor.getValue();
        assertThat(persisted.getRequestIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(persisted.getPayload()).isNull();
    }

    private AgentTaskOperationRespVO invokeLifecycle(AgentTaskAction action) {
        return switch (action) {
            case PAUSE -> taskService.pause(TASK_ID, IDEMPOTENCY_KEY);
            case RESUME -> taskService.resume(TASK_ID, IDEMPOTENCY_KEY);
            case CANCEL -> taskService.cancel(TASK_ID, "需求变更", IDEMPOTENCY_KEY);
            case RE_ENQUEUE -> taskService.reEnqueue(TASK_ID, IDEMPOTENCY_KEY);
            case DELETE -> taskService.deleteTask(TASK_ID, IDEMPOTENCY_KEY);
            default -> throw new IllegalArgumentException("未覆盖的动作: " + action);
        };
    }

    private AgentTaskDO task(String status) {
        return AgentTaskDO.builder()
                .id(TASK_ID)
                .taskNo(TASK_NO)
                .status(status)
                .docVersion(1)
                .executionGeneration(0L)
                .build();
    }

    private AgentTaskDO pausedTask(int docVersion) {
        return AgentTaskDO.builder()
                .id(TASK_ID)
                .taskNo(TASK_NO)
                .status("PAUSED")
                .docVersion(docVersion)
                .executionGeneration(0L)
                .build();
    }

    private AgentTaskOperationLogDO submitLog(Long id) {
        return AgentTaskOperationLogDO.builder()
                .id(id)
                .taskId(TASK_ID)
                .action("SUBMIT")
                .requestIdempotencyKey(IDEMPOTENCY_KEY)
                .build();
    }

    private AgentTaskOperationLogDO lifecycleLog(Long id, AgentTaskAction action) {
        return AgentTaskOperationLogDO.builder()
                .id(id)
                .taskId(TASK_ID)
                .action(action.getValue())
                .fromStatus("PENDING")
                .toStatus("PAUSED")
                .requestIdempotencyKey(IDEMPOTENCY_KEY)
                .build();
    }

    private AgentTaskUpdateDocumentReqVO updateReq(Integer docVersion, String document) {
        AgentTaskUpdateDocumentReqVO reqVO = new AgentTaskUpdateDocumentReqVO();
        reqVO.setDocVersion(docVersion);
        reqVO.setDocument(document);
        reqVO.setTimeoutMinutes(60);
        reqVO.setPriority(50);
        reqVO.setDependsOnTaskId(null);
        return reqVO;
    }

    private TaskDocument validTaskDocument() {
        return TaskDocument.builder()
                .frontMatter(TaskFrontMatter.builder()
                        .taskId(TASK_NO)
                        .title("会员充值优惠券抵扣全栈支持")
                        .targetBranch("feature/TASK-20261001-088")
                        .timeoutMinutes(45)
                        .repoUrl("git@github.com:Yvesjava/TaskForge.git")
                        .baseBranch("main")
                        .build())
                .body("# 需求目标与上下文\n")
                .build();
    }

    private String docText() {
        return "---\n"
                + "taskId: \"TASK-20261001-088\"\n"
                + "title: \"会员充值优惠券抵扣全栈支持\"\n"
                + "targetBranch: \"feature/TASK-20261001-088\"\n"
                + "timeoutMinutes: 45\n"
                + "repoUrl: \"git@github.com:Yvesjava/TaskForge.git\"\n"
                + "baseBranch: \"main\"\n"
                + "---\n";
    }

    private String docTextWithCredential(String credential) {
        return "---\n"
                + "taskId: \"TASK-20261001-088\"\n"
                + "title: \"会员充值优惠券抵扣全栈支持\"\n"
                + "targetBranch: \"feature/TASK-20261001-088\"\n"
                + "timeoutMinutes: 45\n"
                + "repoUrl: \"https://oauth2:" + credential + "@github.com/Yvesjava/TaskForge.git\"\n"
                + "baseBranch: \"main\"\n"
                + "---\n"
                + "# 需求目标与上下文\n"
                + "凭证: " + credential + "\n";
    }

}
