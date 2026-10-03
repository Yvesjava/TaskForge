package cn.iocoder.yudao.module.agent.service.scheduler;

import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.framework.scheduler.AgentSchedulerProperties;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskStateMachine;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskTransitionCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-SCHED-04 租约过期扫描与失败恢复单元测试
 *
 * <p>覆盖 {@link AgentTaskLeaseRecoveryServiceImpl} 的核心契约：有界批次扫描
 * {@code RUNNING AND lease_until < NOW}、通过状态机执行 {@code HEARTBEAT_EXPIRED -> FAILED}、
 * 递增执行代次使旧 Worker 写回失配，并释放对应 Redis 租约。真正的
 * {@code worker_id + execution_generation} 写回守卫位于 {@code AgentTaskMapper.xml} 的
 * {@code markLeaseExpiredFailed}/{@code markSelfVerifiedIfRunning}/{@code markFailedIfRunning}。</p>
 */
@ExtendWith(MockitoExtension.class)
class LeaseRecoveryTest {

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskStateMachine stateMachine;

    @Mock
    private AgentTaskLeaseService taskLeaseService;

    @Mock
    private AgentSchedulerProperties schedulerProperties;

    @InjectMocks
    private AgentTaskLeaseRecoveryServiceImpl recoveryService;

    @Test
    void recoverExpiredLeases_returnsZero_whenNoExpiredTasks() {
        when(schedulerProperties.getRecoveryBatchSize()).thenReturn(50);
        when(taskMapper.selectExpiredRunningTasks(50)).thenReturn(List.of());

        int recovered = recoveryService.recoverExpiredLeases();

        assertThat(recovered).isZero();
        verify(stateMachine, never()).transition(any());
        verify(taskLeaseService, never()).release(any());
    }

    @Test
    void recoverExpiredLeases_scansBoundedBatch() {
        when(schedulerProperties.getRecoveryBatchSize()).thenReturn(50);
        when(taskMapper.selectExpiredRunningTasks(50)).thenReturn(List.of());

        recoveryService.recoverExpiredLeases();

        verify(taskMapper).selectExpiredRunningTasks(50);
    }

    @Test
    void recoverExpiredLeases_transitionsExpiredTaskToFailed_andReleasesLease() {
        AgentTaskDO task = AgentTaskDO.builder()
                .id(1001L)
                .taskNo("TASK-SCHED-04-001")
                .workerId("worker-old")
                .executionGeneration(3L)
                .build();
        when(schedulerProperties.getRecoveryBatchSize()).thenReturn(50);
        when(taskMapper.selectExpiredRunningTasks(50)).thenReturn(List.of(task));

        int recovered = recoveryService.recoverExpiredLeases();

        assertThat(recovered).isEqualTo(1);

        ArgumentCaptor<AgentTaskTransitionCommand> commandCaptor =
                ArgumentCaptor.forClass(AgentTaskTransitionCommand.class);
        verify(stateMachine).transition(commandCaptor.capture());
        AgentTaskTransitionCommand command = commandCaptor.getValue();
        assertThat(command.getTaskId()).isEqualTo(1001L);
        assertThat(command.getTaskNo()).isEqualTo("TASK-SCHED-04-001");
        assertThat(command.getAction()).isEqualTo(AgentTaskAction.HEARTBEAT_EXPIRED);
        assertThat(command.getFromStatus()).isEqualTo(AgentTaskStatus.RUNNING);
        assertThat(command.getGeneration()).isEqualTo(4L);

        verify(taskLeaseService).release(eq(1001L));
    }

    @Test
    void recoverExpiredLeases_incrementsGeneration_invalidatingOldWorkerWriteBack() {
        AgentTaskDO task = AgentTaskDO.builder()
                .id(2002L)
                .taskNo("TASK-SCHED-04-002")
                .workerId("worker-old")
                .executionGeneration(9L)
                .build();
        when(schedulerProperties.getRecoveryBatchSize()).thenReturn(50);
        when(taskMapper.selectExpiredRunningTasks(50)).thenReturn(List.of(task));

        recoveryService.recoverExpiredLeases();

        ArgumentCaptor<AgentTaskTransitionCommand> commandCaptor =
                ArgumentCaptor.forClass(AgentTaskTransitionCommand.class);
        verify(stateMachine).transition(commandCaptor.capture());
        AgentTaskTransitionCommand command = commandCaptor.getValue();

        // 旧 Worker 写回时仍携带 worker-old 与旧代次 9；恢复后状态机以代次 10 落库并清空 worker，
        // 使写回条件 worker_id + execution_generation 不再匹配，从而无法覆盖新结果。
        assertThat(command.getGeneration()).isEqualTo(10L);
        assertThat(command.getWorkerId()).isNull();
    }

    @Test
    void recoverExpiredLeases_handlesMultipleExpiredTasksInBatch() {
        AgentTaskDO first = AgentTaskDO.builder()
                .id(3001L).taskNo("TASK-SCHED-04-A").workerId("worker-a").executionGeneration(1L).build();
        AgentTaskDO second = AgentTaskDO.builder()
                .id(3002L).taskNo("TASK-SCHED-04-B").workerId("worker-b").executionGeneration(5L).build();
        when(schedulerProperties.getRecoveryBatchSize()).thenReturn(50);
        when(taskMapper.selectExpiredRunningTasks(50)).thenReturn(List.of(first, second));

        int recovered = recoveryService.recoverExpiredLeases();

        assertThat(recovered).isEqualTo(2);
        verify(stateMachine, times(2)).transition(any());
        verify(taskLeaseService).release(eq(3001L));
        verify(taskLeaseService).release(eq(3002L));
    }

    @Test
    void recoverExpiredLeases_runsInShortTransaction() throws NoSuchMethodException {
        Method method = AgentTaskLeaseRecoveryServiceImpl.class.getDeclaredMethod("recoverExpiredLeases");

        assertThat(method.getAnnotation(Transactional.class))
                .as("恢复扫描必须在独立短事务内完成，避免长事务持有数据库行锁")
                .isNotNull();
    }

}
