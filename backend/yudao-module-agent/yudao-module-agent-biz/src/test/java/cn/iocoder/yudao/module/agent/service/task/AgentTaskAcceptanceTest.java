package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskOperationRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验收、打回与合并冲突人工入口的验收测试。
 *
 * <p>覆盖服务层入口装配（幂等、反馈校验、命令字段），以及状态机审计记录
 * 是否完整写入反馈、操作者、文档版本与前后状态。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskAcceptanceTest {

    private static final Long TASK_ID = 9012L;
    private static final String IDEMPOTENCY_KEY = "accept_20261003_0001";
    private static final String REJECT_KEY = "reject_20261003_0001";
    private static final String MERGE_KEY = "merge_20261003_0001";

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskOperationLogMapper operationLogMapper;

    @Mock
    private AgentTaskStateMachine stateMachine;

    @InjectMocks
    private AgentTaskServiceImpl taskService;

    private AgentTaskStateMachineImpl realStateMachine;

    @BeforeEach
    void setUp() {
        realStateMachine = new AgentTaskStateMachineImpl();
        ReflectionTestUtils.setField(realStateMachine, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(realStateMachine, "operationLogMapper", operationLogMapper);
    }

    @Test
    void accept_delegatesToStateMachineAndReturnsRecordedVersion() {
        AgentTaskDO before = task("WAITING_ACCEPTANCE", 5);
        AgentTaskDO after = task("ACCEPTED", 5);
        AgentTaskOperationLogDO log = operationLog(200L, "ACCEPT", "WAITING_ACCEPTANCE", "ACCEPTED", 5);
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY)).thenReturn(null, log);
        when(taskMapper.selectById(TASK_ID)).thenReturn(before, after);
        when(stateMachine.transition(any())).thenReturn(AgentTaskStatus.ACCEPTED);

        AgentTaskOperationRespVO response = taskService.accept(TASK_ID, IDEMPOTENCY_KEY);

        assertThat(response.getTaskId()).isEqualTo(TASK_ID);
        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getDocVersion()).isEqualTo(5);
        assertThat(response.getOperationId()).isEqualTo("op_200");

        AgentTaskTransitionCommand command = captureTransition();
        assertThat(command.getAction()).isEqualTo(AgentTaskAction.ACCEPT);
        assertThat(command.getFromStatus()).isEqualTo(AgentTaskStatus.WAITING_ACCEPTANCE);
        assertThat(command.getDocVersion()).isEqualTo(5);
        assertThat(command.getFeedback()).isNull();
        assertThat(command.getRequestIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    void reject_requiresNonBlankFeedback() {
        assertThatThrownBy(() -> taskService.reject(TASK_ID, "   ", REJECT_KEY))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_REJECT_FEEDBACK_REQUIRED.getCode()));
        verify(stateMachine, never()).transition(any());
    }

    @Test
    void reject_rejectsOverlongFeedback() {
        String feedback = "x".repeat(2001);

        assertThatThrownBy(() -> taskService.reject(TASK_ID, feedback, REJECT_KEY))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_REJECT_FEEDBACK_INVALID.getCode()));
        verify(stateMachine, never()).transition(any());
    }

    @Test
    void reject_delegatesWithFeedbackAndVersion() {
        String feedback = "实现与设计稿不一致";
        AgentTaskDO before = task("WAITING_ACCEPTANCE", 6);
        AgentTaskDO after = task("REJECTED", 6);
        AgentTaskOperationLogDO log = operationLog(201L, "REJECT", "WAITING_ACCEPTANCE", "REJECTED", 6, feedback);
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, REJECT_KEY)).thenReturn(null, log);
        when(taskMapper.selectById(TASK_ID)).thenReturn(before, after);
        when(stateMachine.transition(any())).thenReturn(AgentTaskStatus.REJECTED);

        AgentTaskOperationRespVO response = taskService.reject(TASK_ID, feedback, REJECT_KEY);

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getDocVersion()).isEqualTo(6);
        assertThat(response.getOperationId()).isEqualTo("op_201");

        AgentTaskTransitionCommand command = captureTransition();
        assertThat(command.getAction()).isEqualTo(AgentTaskAction.REJECT);
        assertThat(command.getFromStatus()).isEqualTo(AgentTaskStatus.WAITING_ACCEPTANCE);
        assertThat(command.getDocVersion()).isEqualTo(6);
        assertThat(command.getFeedback()).isEqualTo(feedback);
        assertThat(command.getRequestIdempotencyKey()).isEqualTo(REJECT_KEY);
    }

    @Test
    void markMergeConflict_delegatesWithAcceptedFromStatusAndVersion() {
        AgentTaskDO before = task("ACCEPTED", 7);
        AgentTaskDO after = task("MERGE_CONFLICT_PENDING_MANUAL", 7);
        AgentTaskOperationLogDO log = operationLog(202L, "MERGE_CONFLICT", "ACCEPTED",
                "MERGE_CONFLICT_PENDING_MANUAL", 7);
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, MERGE_KEY)).thenReturn(null, log);
        when(taskMapper.selectById(TASK_ID)).thenReturn(before, after);
        when(stateMachine.transition(any())).thenReturn(AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL);

        AgentTaskOperationRespVO response = taskService.markMergeConflict(TASK_ID, MERGE_KEY);

        assertThat(response.getStatus()).isEqualTo("MERGE_CONFLICT_PENDING_MANUAL");
        assertThat(response.getDocVersion()).isEqualTo(7);
        assertThat(response.getOperationId()).isEqualTo("op_202");

        AgentTaskTransitionCommand command = captureTransition();
        assertThat(command.getAction()).isEqualTo(AgentTaskAction.MERGE_CONFLICT);
        assertThat(command.getFromStatus()).isEqualTo(AgentTaskStatus.ACCEPTED);
        assertThat(command.getDocVersion()).isEqualTo(7);
        assertThat(command.getRequestIdempotencyKey()).isEqualTo(MERGE_KEY);
    }

    @Test
    void accept_duplicateIdempotencyKey_returnsFirstResultWithoutTransition() {
        AgentTaskOperationLogDO existing = operationLog(200L, "ACCEPT", "WAITING_ACCEPTANCE", "ACCEPTED", 5);
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY)).thenReturn(existing);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED", 5));

        AgentTaskOperationRespVO response = taskService.accept(TASK_ID, IDEMPOTENCY_KEY);

        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getDocVersion()).isEqualTo(5);
        assertThat(response.getOperationId()).isEqualTo("op_200");
        verify(stateMachine, never()).transition(any());
    }

    @Test
    void stateMachine_accept_recordsFeedbackOperatorVersionAndStatuses() {
        when(taskMapper.acceptIfWaitingAcceptance(TASK_ID)).thenReturn(1);

        AgentTaskStatus result = realStateMachine.transition(command(
                AgentTaskStatus.WAITING_ACCEPTANCE, AgentTaskAction.ACCEPT, null, 5, 42L, "tester", IDEMPOTENCY_KEY));

        assertThat(result).isEqualTo(AgentTaskStatus.ACCEPTED);
        AgentTaskOperationLogDO log = captureAudit();
        assertThat(log.getAction()).isEqualTo("ACCEPT");
        assertThat(log.getFromStatus()).isEqualTo("WAITING_ACCEPTANCE");
        assertThat(log.getToStatus()).isEqualTo("ACCEPTED");
        assertThat(log.getDocVersion()).isEqualTo(5);
        assertThat(log.getFeedback()).isNull();
        assertThat(log.getOperatorId()).isEqualTo(42L);
        assertThat(log.getOperatorName()).isEqualTo("tester");
        assertThat(log.getRequestIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    void stateMachine_reject_recordsFeedbackOperatorVersionAndStatuses() {
        when(taskMapper.rejectIfWaitingAcceptance(TASK_ID)).thenReturn(1);

        AgentTaskStatus result = realStateMachine.transition(command(
                AgentTaskStatus.WAITING_ACCEPTANCE, AgentTaskAction.REJECT, "样式问题", 6, 43L, "qa", REJECT_KEY));

        assertThat(result).isEqualTo(AgentTaskStatus.REJECTED);
        AgentTaskOperationLogDO log = captureAudit();
        assertThat(log.getAction()).isEqualTo("REJECT");
        assertThat(log.getFromStatus()).isEqualTo("WAITING_ACCEPTANCE");
        assertThat(log.getToStatus()).isEqualTo("REJECTED");
        assertThat(log.getDocVersion()).isEqualTo(6);
        assertThat(log.getFeedback()).isEqualTo("样式问题");
        assertThat(log.getOperatorId()).isEqualTo(43L);
        assertThat(log.getOperatorName()).isEqualTo("qa");
    }

    @Test
    void stateMachine_mergeConflict_recordsVersionOperatorAndStatuses() {
        when(taskMapper.markMergeConflictIfAccepted(TASK_ID)).thenReturn(1);

        AgentTaskStatus result = realStateMachine.transition(command(
                AgentTaskStatus.ACCEPTED, AgentTaskAction.MERGE_CONFLICT, null, 7, 44L, "ops", MERGE_KEY));

        assertThat(result).isEqualTo(AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL);
        AgentTaskOperationLogDO log = captureAudit();
        assertThat(log.getAction()).isEqualTo("MERGE_CONFLICT");
        assertThat(log.getFromStatus()).isEqualTo("ACCEPTED");
        assertThat(log.getToStatus()).isEqualTo("MERGE_CONFLICT_PENDING_MANUAL");
        assertThat(log.getDocVersion()).isEqualTo(7);
        assertThat(log.getOperatorId()).isEqualTo(44L);
        assertThat(log.getOperatorName()).isEqualTo("ops");
    }

    private AgentTaskTransitionCommand captureTransition() {
        ArgumentCaptor<AgentTaskTransitionCommand> captor =
                ArgumentCaptor.forClass(AgentTaskTransitionCommand.class);
        verify(stateMachine).transition(captor.capture());
        return captor.getValue();
    }

    private AgentTaskOperationLogDO captureAudit() {
        ArgumentCaptor<AgentTaskOperationLogDO> captor =
                ArgumentCaptor.forClass(AgentTaskOperationLogDO.class);
        verify(operationLogMapper).insert(captor.capture());
        return captor.getValue();
    }

    private static AgentTaskTransitionCommand command(AgentTaskStatus from,
                                                      AgentTaskAction action,
                                                      String feedback,
                                                      Integer docVersion,
                                                      Long operatorId,
                                                      String operatorName,
                                                      String idempotencyKey) {
        return AgentTaskTransitionCommand.builder()
                .taskId(TASK_ID)
                .taskNo("TASK-20261001-088")
                .action(action)
                .fromStatus(from)
                .feedback(feedback)
                .docVersion(docVersion)
                .requestIdempotencyKey(idempotencyKey)
                .operatorId(operatorId)
                .operatorName(operatorName)
                .build();
    }

    private static AgentTaskDO task(String status, Integer docVersion) {
        return AgentTaskDO.builder()
                .id(TASK_ID)
                .taskNo("TASK-20261001-088")
                .status(status)
                .docVersion(docVersion)
                .executionGeneration(0L)
                .build();
    }

    private static AgentTaskOperationLogDO operationLog(Long id,
                                                        String action,
                                                        String from,
                                                        String to,
                                                        Integer docVersion) {
        return operationLog(id, action, from, to, docVersion, null);
    }

    private static AgentTaskOperationLogDO operationLog(Long id,
                                                        String action,
                                                        String from,
                                                        String to,
                                                        Integer docVersion,
                                                        String feedback) {
        return AgentTaskOperationLogDO.builder()
                .id(id)
                .taskId(TASK_ID)
                .action(action)
                .fromStatus(from)
                .toStatus(to)
                .docVersion(docVersion)
                .feedback(feedback)
                .build();
    }

}
