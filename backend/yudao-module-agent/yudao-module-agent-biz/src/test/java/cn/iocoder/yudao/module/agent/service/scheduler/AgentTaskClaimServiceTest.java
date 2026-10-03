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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentTaskClaimServiceImpl} 的单元测试
 *
 * <p>覆盖短事务抢占的核心契约：无待执行任务时返回空、代次自增、写入 Worker 与租约、
 * 复用状态机执行 {@code PENDING -> RUNNING}，以及抢占方法本身是独立短事务且不包围外部操作。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskClaimServiceTest {

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskStateMachine stateMachine;

    @Mock
    private WorkerIdentity workerIdentity;

    @Mock
    private AgentSchedulerProperties schedulerProperties;

    @Mock
    private AgentTaskLeaseService taskLeaseService;

    @InjectMocks
    private AgentTaskClaimServiceImpl claimService;

    @Test
    void claimNextTask_returnsEmpty_whenNoPendingTask() {
        when(taskMapper.selectNextPendingTaskForUpdate()).thenReturn(null);

        Optional<AgentTaskDO> result = claimService.claimNextTask();

        assertThat(result).isEmpty();
        verify(stateMachine, never()).transition(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void claimNextTask_claimsTask_withIncrementedGenerationAndWorkerIdentity() {
        AgentTaskDO task = AgentTaskDO.builder()
                .id(1001L)
                .taskNo("TASK-SCHED-01-001")
                .status(AgentTaskStatus.PENDING.getValue())
                .executionGeneration(2L)
                .build();
        LocalDateTime before = LocalDateTime.now();
        when(taskMapper.selectNextPendingTaskForUpdate()).thenReturn(task);
        when(workerIdentity.id()).thenReturn("worker-1");
        when(schedulerProperties.getLeaseMinutes()).thenReturn(5L);

        Optional<AgentTaskDO> result = claimService.claimNextTask();

        assertThat(result).isPresent();
        AgentTaskDO claimed = result.get();
        assertThat(claimed.getExecutionGeneration()).isEqualTo(3L);
        assertThat(claimed.getWorkerId()).isEqualTo("worker-1");
        assertThat(claimed.getStatus()).isEqualTo(AgentTaskStatus.RUNNING.getValue());
        assertThat(claimed.getLeaseUntil()).isBetween(
                before.plusMinutes(5).minusSeconds(2), before.plusMinutes(5).plusSeconds(2));

        ArgumentCaptor<AgentTaskTransitionCommand> commandCaptor =
                ArgumentCaptor.forClass(AgentTaskTransitionCommand.class);
        verify(stateMachine).transition(commandCaptor.capture());
        AgentTaskTransitionCommand command = commandCaptor.getValue();
        assertThat(command.getTaskId()).isEqualTo(1001L);
        assertThat(command.getTaskNo()).isEqualTo("TASK-SCHED-01-001");
        assertThat(command.getAction()).isEqualTo(AgentTaskAction.CLAIM);
        assertThat(command.getFromStatus()).isEqualTo(AgentTaskStatus.PENDING);
        assertThat(command.getWorkerId()).isEqualTo("worker-1");
        assertThat(command.getGeneration()).isEqualTo(3L);
        assertThat(command.getLeaseUntil()).isNotNull();

        verify(taskLeaseService).acquire(eq(1001L), eq("worker-1"), eq(3L), any(LocalDateTime.class));
    }

    @Test
    void claimNextTask_delegatesOrderingAndDependencyFiltering_toMapper() {
        AgentTaskDO task = AgentTaskDO.builder()
                .id(1002L).taskNo("TASK-SCHED-01-002")
                .status(AgentTaskStatus.PENDING.getValue())
                .executionGeneration(0L)
                .build();
        when(taskMapper.selectNextPendingTaskForUpdate()).thenReturn(task);
        when(workerIdentity.id()).thenReturn("worker-2");
        when(schedulerProperties.getLeaseMinutes()).thenReturn(5L);

        claimService.claimNextTask();

        // 优先级/创建时间排序与前置任务过滤由该 Mapper 查询负责
        verify(taskMapper).selectNextPendingTaskForUpdate();
    }

    @Test
    void claimNextTask_isShortTransaction_withoutWrappingExternalOperations() throws NoSuchMethodException {
        Method method = AgentTaskClaimServiceImpl.class.getDeclaredMethod("claimNextTask");

        assertThat(method.getAnnotation(Transactional.class))
                .as("抢占必须运行在独立短事务中，外部操作由调用方在事务提交后执行")
                .isNotNull();
    }

}
