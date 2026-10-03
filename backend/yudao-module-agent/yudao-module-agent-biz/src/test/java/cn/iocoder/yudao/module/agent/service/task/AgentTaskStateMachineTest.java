package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AgentTaskStateMachine} 的单元测试
 *
 * <p>覆盖状态枚举、合法转换矩阵、统一服务入口与非法转换拒绝。
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskStateMachineTest {

    private static final Long TASK_ID = 9012L;

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskOperationLogMapper operationLogMapper;

    @InjectMocks
    private AgentTaskStateMachineImpl stateMachine;

    @Test
    void statusEnumExposesAllTwelveStatuses() {
        assertThat(AgentTaskStatus.values()).hasSize(12);
        for (AgentTaskStatus status : AgentTaskStatus.values()) {
            assertThat(AgentTaskStatus.valueOfCode(status.getValue())).isEqualTo(status);
        }
    }

    @Test
    void transitionMatrixAllowsEveryDocumentedTransition() {
        assertLegal(AgentTaskStatus.PENDING, AgentTaskAction.PAUSE, AgentTaskStatus.PAUSED);
        assertLegal(AgentTaskStatus.PENDING, AgentTaskAction.CLAIM, AgentTaskStatus.RUNNING);
        assertLegal(AgentTaskStatus.PENDING, AgentTaskAction.CANCEL, AgentTaskStatus.CANCELED);

        assertLegal(AgentTaskStatus.PAUSED, AgentTaskAction.EDIT, AgentTaskStatus.PAUSED);
        assertLegal(AgentTaskStatus.PAUSED, AgentTaskAction.RESUME, AgentTaskStatus.PENDING);
        assertLegal(AgentTaskStatus.PAUSED, AgentTaskAction.RESET, AgentTaskStatus.RESETTING);
        assertLegal(AgentTaskStatus.PAUSED, AgentTaskAction.CANCEL, AgentTaskStatus.CANCELED);

        assertLegal(AgentTaskStatus.RUNNING, AgentTaskAction.SELF_VERIFY_PASS, AgentTaskStatus.WAITING_ACCEPTANCE);
        assertLegal(AgentTaskStatus.RUNNING, AgentTaskAction.TIMEOUT, AgentTaskStatus.FAILED);
        assertLegal(AgentTaskStatus.RUNNING, AgentTaskAction.ERROR, AgentTaskStatus.FAILED);
        assertLegal(AgentTaskStatus.RUNNING, AgentTaskAction.HEARTBEAT_EXPIRED, AgentTaskStatus.FAILED);

        assertLegal(AgentTaskStatus.WAITING_ACCEPTANCE, AgentTaskAction.ACCEPT, AgentTaskStatus.ACCEPTED);
        assertLegal(AgentTaskStatus.WAITING_ACCEPTANCE, AgentTaskAction.REJECT, AgentTaskStatus.REJECTED);

        assertLegal(AgentTaskStatus.ACCEPTED, AgentTaskAction.MERGE_PASS, AgentTaskStatus.COMPLETED);
        assertLegal(AgentTaskStatus.ACCEPTED, AgentTaskAction.MERGE_CONFLICT,
                AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL);

        assertLegal(AgentTaskStatus.REJECTED, AgentTaskAction.RE_ENQUEUE, AgentTaskStatus.PENDING);
        assertLegal(AgentTaskStatus.FAILED, AgentTaskAction.RE_ENQUEUE, AgentTaskStatus.PENDING);
        assertLegal(AgentTaskStatus.CANCELED, AgentTaskAction.RE_ENQUEUE, AgentTaskStatus.PENDING);
        assertLegal(AgentTaskStatus.CANCELED, AgentTaskAction.DELETE, AgentTaskStatus.DELETED);

        assertLegal(AgentTaskStatus.RESETTING, AgentTaskAction.CLEANUP_PASS, AgentTaskStatus.PENDING);
        assertLegal(AgentTaskStatus.RESETTING, AgentTaskAction.CLEANUP_PASS, AgentTaskStatus.PAUSED);
    }

    @Test
    void transitionMatrixRejectsUnlistedTransitions() {
        assertIllegal(AgentTaskStatus.COMPLETED, AgentTaskAction.PAUSE, AgentTaskStatus.PAUSED);
        assertIllegal(AgentTaskStatus.PENDING, AgentTaskAction.ACCEPT, AgentTaskStatus.ACCEPTED);
        assertIllegal(AgentTaskStatus.PENDING, AgentTaskAction.PAUSE, AgentTaskStatus.RUNNING);
        assertIllegal(AgentTaskStatus.RUNNING, AgentTaskAction.RESUME, AgentTaskStatus.PENDING);
        assertIllegal(AgentTaskStatus.CANCELED, AgentTaskAction.CLAIM, AgentTaskStatus.RUNNING);
        assertIllegal(AgentTaskStatus.DELETED, AgentTaskAction.RE_ENQUEUE, AgentTaskStatus.PENDING);
    }

    @Test
    void transition_pauseDelegatesToConditionalUpdateAndWritesAudit() {
        when(taskMapper.pauseIfPending(TASK_ID)).thenReturn(1);

        AgentTaskStatus result = stateMachine.transition(command(AgentTaskStatus.PENDING, AgentTaskAction.PAUSE));

        assertThat(result).isEqualTo(AgentTaskStatus.PAUSED);
        verify(taskMapper).pauseIfPending(TASK_ID);
        assertAudit(AgentTaskAction.PAUSE, "PENDING", "PAUSED");
    }

    @Test
    void transition_claimDelegatesToMarkTaskRunning() {
        when(taskMapper.markTaskRunning(eq(TASK_ID), eq("worker-1"), any(LocalDateTime.class), eq(3L))).thenReturn(1);

        AgentTaskTransitionCommand command = AgentTaskTransitionCommand.builder()
                .taskId(TASK_ID)
                .action(AgentTaskAction.CLAIM)
                .fromStatus(AgentTaskStatus.PENDING)
                .workerId("worker-1")
                .leaseUntil(LocalDateTime.now().plusMinutes(30))
                .generation(3L)
                .build();

        AgentTaskStatus result = stateMachine.transition(command);

        assertThat(result).isEqualTo(AgentTaskStatus.RUNNING);
        verify(taskMapper).markTaskRunning(eq(TASK_ID), eq("worker-1"), any(LocalDateTime.class), eq(3L));
        assertAudit(AgentTaskAction.CLAIM, "PENDING", "RUNNING");
    }

    @Test
    void transition_editDelegatesToDocumentUpdateWithOptimisticLock() {
        when(taskMapper.updateDocumentIfPaused(eq(TASK_ID), eq(2), eq("new doc"), eq(45), eq(10), isNull()))
                .thenReturn(1);

        AgentTaskTransitionCommand command = AgentTaskTransitionCommand.builder()
                .taskId(TASK_ID)
                .action(AgentTaskAction.EDIT)
                .fromStatus(AgentTaskStatus.PAUSED)
                .expectedDocVersion(2)
                .taskDoc("new doc")
                .timeoutMinutes(45)
                .priority(10)
                .build();

        AgentTaskStatus result = stateMachine.transition(command);

        assertThat(result).isEqualTo(AgentTaskStatus.PAUSED);
        verify(taskMapper).updateDocumentIfPaused(eq(TASK_ID), eq(2), eq("new doc"), eq(45), eq(10), isNull());
        assertAudit(AgentTaskAction.EDIT, "PAUSED", "PAUSED");
    }

    @Test
    void transition_selfVerifyPassDelegatesToMarkSelfVerified() {
        when(taskMapper.markSelfVerifiedIfRunning(TASK_ID, "worker-1", 5L)).thenReturn(1);

        AgentTaskTransitionCommand command = AgentTaskTransitionCommand.builder()
                .taskId(TASK_ID)
                .action(AgentTaskAction.SELF_VERIFY_PASS)
                .fromStatus(AgentTaskStatus.RUNNING)
                .workerId("worker-1")
                .generation(5L)
                .build();

        AgentTaskStatus result = stateMachine.transition(command);

        assertThat(result).isEqualTo(AgentTaskStatus.WAITING_ACCEPTANCE);
        verify(taskMapper).markSelfVerifiedIfRunning(TASK_ID, "worker-1", 5L);
        assertAudit(AgentTaskAction.SELF_VERIFY_PASS, "RUNNING", "WAITING_ACCEPTANCE");
    }

    @Test
    void transition_reEnqueueDelegatesToReEnqueueIfEnded() {
        when(taskMapper.reEnqueueIfEnded(TASK_ID)).thenReturn(1);

        AgentTaskStatus result = stateMachine.transition(command(AgentTaskStatus.FAILED, AgentTaskAction.RE_ENQUEUE));

        assertThat(result).isEqualTo(AgentTaskStatus.PENDING);
        verify(taskMapper).reEnqueueIfEnded(TASK_ID);
        assertAudit(AgentTaskAction.RE_ENQUEUE, "FAILED", "PENDING");
    }

    @Test
    void transition_deleteDelegatesToSoftDelete() {
        when(taskMapper.softDeleteIfCanceled(TASK_ID)).thenReturn(1);

        AgentTaskStatus result = stateMachine.transition(command(AgentTaskStatus.CANCELED, AgentTaskAction.DELETE));

        assertThat(result).isEqualTo(AgentTaskStatus.DELETED);
        verify(taskMapper).softDeleteIfCanceled(TASK_ID);
        assertAudit(AgentTaskAction.DELETE, "CANCELED", "DELETED");
    }

    @Test
    void transition_cleanupPassWithExplicitTargetDelegatesToFinishReset() {
        when(taskMapper.finishReset(TASK_ID, "PAUSED")).thenReturn(1);

        AgentTaskTransitionCommand command = AgentTaskTransitionCommand.builder()
                .taskId(TASK_ID)
                .action(AgentTaskAction.CLEANUP_PASS)
                .fromStatus(AgentTaskStatus.RESETTING)
                .targetStatus(AgentTaskStatus.PAUSED)
                .build();

        AgentTaskStatus result = stateMachine.transition(command);

        assertThat(result).isEqualTo(AgentTaskStatus.PAUSED);
        verify(taskMapper).finishReset(TASK_ID, "PAUSED");
        assertAudit(AgentTaskAction.CLEANUP_PASS, "RESETTING", "PAUSED");
    }

    @Test
    void transition_unlistedFromStatusThrowsConflictWithoutTouchingPersistence() {
        assertThatThrownBy(() -> stateMachine.transition(command(AgentTaskStatus.COMPLETED, AgentTaskAction.PAUSE)))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_STATUS_TRANSITION_NOT_ALLOWED.getCode()));
        verifyNoInteractions(taskMapper);
        verifyNoInteractions(operationLogMapper);
    }

    @Test
    void transition_unlistedTargetThrowsConflictWithoutTouchingPersistence() {
        AgentTaskTransitionCommand command = AgentTaskTransitionCommand.builder()
                .taskId(TASK_ID)
                .action(AgentTaskAction.PAUSE)
                .fromStatus(AgentTaskStatus.PENDING)
                .targetStatus(AgentTaskStatus.RUNNING)
                .build();

        assertThatThrownBy(() -> stateMachine.transition(command))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_STATUS_TRANSITION_NOT_ALLOWED.getCode()));
        verifyNoInteractions(taskMapper);
        verifyNoInteractions(operationLogMapper);
    }

    @Test
    void transition_ambiguousTargetRequiresExplicitTarget() {
        assertThatThrownBy(() -> stateMachine.transition(command(AgentTaskStatus.RESETTING, AgentTaskAction.CLEANUP_PASS)))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_STATUS_TRANSITION_TARGET_REQUIRED.getCode()));
        verifyNoInteractions(taskMapper);
        verifyNoInteractions(operationLogMapper);
    }

    @Test
    void transition_conditionalUpdateReturnsZeroThrowsConflictWithoutAudit() {
        when(taskMapper.pauseIfPending(TASK_ID)).thenReturn(0);

        assertThatThrownBy(() -> stateMachine.transition(command(AgentTaskStatus.PENDING, AgentTaskAction.PAUSE)))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_STATUS_UPDATE_CONFLICT.getCode()));
        verify(taskMapper).pauseIfPending(TASK_ID);
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    private static AgentTaskTransitionCommand command(AgentTaskStatus from, AgentTaskAction action) {
        return AgentTaskTransitionCommand.builder()
                .taskId(TASK_ID)
                .action(action)
                .fromStatus(from)
                .build();
    }

    private static void assertLegal(AgentTaskStatus from, AgentTaskAction action, AgentTaskStatus to) {
        assertThat(AgentTaskTransitionMatrix.isLegal(from, action, to))
                .as("%s --%s--> %s should be legal", from, action, to)
                .isTrue();
        assertThat(AgentTaskTransitionMatrix.legalTargets(from, action)).contains(to);
    }

    private static void assertIllegal(AgentTaskStatus from, AgentTaskAction action, AgentTaskStatus to) {
        assertThat(AgentTaskTransitionMatrix.isLegal(from, action, to))
                .as("%s --%s--> %s should be illegal", from, action, to)
                .isFalse();
    }

    private void assertAudit(AgentTaskAction action, String from, String to) {
        ArgumentCaptor<AgentTaskOperationLogDO> captor = ArgumentCaptor.forClass(AgentTaskOperationLogDO.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getTaskId()).isEqualTo(TASK_ID);
        assertThat(captor.getValue().getAction()).isEqualTo(action.getValue());
        assertThat(captor.getValue().getFromStatus()).isEqualTo(from);
        assertThat(captor.getValue().getToStatus()).isEqualTo(to);
    }

}
