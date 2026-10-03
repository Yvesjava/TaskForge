package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskOperationRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 任务生命周期动作（暂停/恢复/取消/重投/软删除）单元测试。
 *
 * <p>通过真实的 {@link AgentTaskStateMachineImpl} 与 Mock 化的持久层，验证合法状态正常流转、
 * 非法状态返回业务错误且不产生任何数据变更。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskLifecycleTest {

    private static final Long TASK_ID = 9012L;
    private static final String TASK_NO = "TASK-20261001-088";
    private static final String IDEMPOTENCY_KEY = "req_20261003_0001";

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskOperationLogMapper operationLogMapper;

    private AgentTaskStateMachineImpl stateMachine;

    private AgentTaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        stateMachine = new AgentTaskStateMachineImpl();
        ReflectionTestUtils.setField(stateMachine, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(stateMachine, "operationLogMapper", operationLogMapper);

        taskService = new AgentTaskServiceImpl();
        ReflectionTestUtils.setField(taskService, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(taskService, "operationLogMapper", operationLogMapper);
        ReflectionTestUtils.setField(taskService, "stateMachine", stateMachine);
    }

    @Test
    void pause_pendingTask_transitionsToPaused() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY))
                .thenReturn(null, log(88L, "PAUSE", "PENDING", "PAUSED"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("PENDING"), task("PAUSED"));
        when(taskMapper.pauseIfPending(TASK_ID)).thenReturn(1);

        AgentTaskOperationRespVO response = taskService.pause(TASK_ID, IDEMPOTENCY_KEY);

        assertThat(response.getStatus()).isEqualTo("PAUSED");
        assertThat(response.getTaskNo()).isEqualTo(TASK_NO);
        assertThat(response.getOperationId()).isEqualTo("op_88");
        verify(taskMapper).pauseIfPending(TASK_ID);
        assertAudit("PAUSE", "PENDING", "PAUSED");
    }

    @Test
    void resume_pausedTask_transitionsToPending() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY))
                .thenReturn(null, log(89L, "RESUME", "PAUSED", "PENDING"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("PAUSED"), task("PENDING"));
        when(taskMapper.resumeIfPaused(TASK_ID)).thenReturn(1);

        AgentTaskOperationRespVO response = taskService.resume(TASK_ID, IDEMPOTENCY_KEY);

        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(taskMapper).resumeIfPaused(TASK_ID);
        assertAudit("RESUME", "PAUSED", "PENDING");
    }

    @Test
    void cancel_pendingTask_transitionsToCanceledWithReason() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY))
                .thenReturn(null, log(90L, "CANCEL", "PENDING", "CANCELED"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("PENDING"), task("CANCELED"));
        when(taskMapper.cancelIfPendingOrPaused(eq(TASK_ID), eq("需求变更"))).thenReturn(1);

        AgentTaskOperationRespVO response = taskService.cancel(TASK_ID, "需求变更", IDEMPOTENCY_KEY);

        assertThat(response.getStatus()).isEqualTo("CANCELED");
        verify(taskMapper).cancelIfPendingOrPaused(TASK_ID, "需求变更");

        ArgumentCaptor<AgentTaskOperationLogDO> captor = ArgumentCaptor.forClass(AgentTaskOperationLogDO.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getFeedback()).isEqualTo("需求变更");
        assertThat(captor.getValue().getFromStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getToStatus()).isEqualTo("CANCELED");
    }

    @Test
    void reEnqueue_canceledTask_transitionsToPending() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY))
                .thenReturn(null, log(91L, "RE_ENQUEUE", "CANCELED", "PENDING"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("CANCELED"), task("PENDING"));
        when(taskMapper.reEnqueueIfEnded(TASK_ID)).thenReturn(1);

        AgentTaskOperationRespVO response = taskService.reEnqueue(TASK_ID, IDEMPOTENCY_KEY);

        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(taskMapper).reEnqueueIfEnded(TASK_ID);
        assertAudit("RE_ENQUEUE", "CANCELED", "PENDING");
    }

    @Test
    void deleteTask_canceledTask_transitionsToDeleted() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY))
                .thenReturn(null, log(92L, "DELETE", "CANCELED", "DELETED"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("CANCELED"), task("DELETED"));
        when(taskMapper.softDeleteIfCanceled(TASK_ID)).thenReturn(1);

        AgentTaskOperationRespVO response = taskService.deleteTask(TASK_ID, IDEMPOTENCY_KEY);

        assertThat(response.getStatus()).isEqualTo("DELETED");
        verify(taskMapper).softDeleteIfCanceled(TASK_ID);
        assertAudit("DELETE", "CANCELED", "DELETED");
    }

    @Test
    void pause_illegalStatus_throwsConflictWithoutChangingData() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("RUNNING"));

        assertThatThrownBy(() -> taskService.pause(TASK_ID, IDEMPOTENCY_KEY))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_STATUS_TRANSITION_NOT_ALLOWED.getCode()));

        verify(taskMapper, never()).pauseIfPending(anyLong());
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    @Test
    void resume_illegalStatus_throwsConflictWithoutChangingData() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("PENDING"));

        assertThatThrownBy(() -> taskService.resume(TASK_ID, IDEMPOTENCY_KEY))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_STATUS_TRANSITION_NOT_ALLOWED.getCode()));

        verify(taskMapper, never()).resumeIfPaused(anyLong());
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    @Test
    void cancel_illegalStatus_throwsConflictWithoutChangingData() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("RUNNING"));

        assertThatThrownBy(() -> taskService.cancel(TASK_ID, "原因", IDEMPOTENCY_KEY))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_STATUS_TRANSITION_NOT_ALLOWED.getCode()));

        verify(taskMapper, never()).cancelIfPendingOrPaused(anyLong(), any());
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    @Test
    void reEnqueue_illegalStatus_throwsConflictWithoutChangingData() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("PENDING"));

        assertThatThrownBy(() -> taskService.reEnqueue(TASK_ID, IDEMPOTENCY_KEY))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_STATUS_TRANSITION_NOT_ALLOWED.getCode()));

        verify(taskMapper, never()).reEnqueueIfEnded(anyLong());
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    @Test
    void deleteTask_illegalStatus_throwsConflictWithoutChangingData() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("PENDING"));

        assertThatThrownBy(() -> taskService.deleteTask(TASK_ID, IDEMPOTENCY_KEY))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_STATUS_TRANSITION_NOT_ALLOWED.getCode()));

        verify(taskMapper, never()).softDeleteIfCanceled(anyLong());
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    @Test
    void pause_duplicateIdempotencyKey_returnsFirstResultWithoutReTransition() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY))
                .thenReturn(log(88L, "PAUSE", "PENDING", "PAUSED"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("PAUSED"));

        AgentTaskOperationRespVO response = taskService.pause(TASK_ID, IDEMPOTENCY_KEY);

        assertThat(response.getOperationId()).isEqualTo("op_88");
        assertThat(response.getStatus()).isEqualTo("PAUSED");
        verify(taskMapper, never()).pauseIfPending(anyLong());
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    @Test
    void pause_missingIdempotencyKey_throws() {
        assertThatThrownBy(() -> taskService.pause(TASK_ID, " "))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_SUBMIT_IDEMPOTENCY_KEY_REQUIRED.getCode()));

        verifyNoInteractions(taskMapper, operationLogMapper);
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

    private AgentTaskOperationLogDO log(Long id, String action, String from, String to) {
        return AgentTaskOperationLogDO.builder()
                .id(id)
                .taskId(TASK_ID)
                .action(action)
                .fromStatus(from)
                .toStatus(to)
                .build();
    }

    private void assertAudit(String action, String from, String to) {
        ArgumentCaptor<AgentTaskOperationLogDO> captor = ArgumentCaptor.forClass(AgentTaskOperationLogDO.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getTaskId()).isEqualTo(TASK_ID);
        assertThat(captor.getValue().getAction()).isEqualTo(action);
        assertThat(captor.getValue().getFromStatus()).isEqualTo(from);
        assertThat(captor.getValue().getToStatus()).isEqualTo(to);
    }

}
