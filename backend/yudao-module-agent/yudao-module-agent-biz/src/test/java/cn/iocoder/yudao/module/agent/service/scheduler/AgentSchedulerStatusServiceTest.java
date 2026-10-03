package cn.iocoder.yudao.module.agent.service.scheduler;

import cn.iocoder.yudao.module.agent.controller.admin.task.vo.scheduler.AgentSchedulerStatusRespVO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentSchedulerStatusServiceImpl} 的单元测试
 *
 * <p>覆盖调度器运行状态指标的三项口径：队列长度、运行中任务数、租约异常任务数。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentSchedulerStatusServiceTest {

    @Mock
    private AgentTaskMapper taskMapper;

    @InjectMocks
    private AgentSchedulerStatusServiceImpl schedulerStatusService;

    @Test
    void getStatus_assemblesQueueRunningAndLeaseAnomalyMetrics() {
        LocalDateTime before = LocalDateTime.now();
        when(taskMapper.countPendingTasks()).thenReturn(12L);
        when(taskMapper.countRunningTasks()).thenReturn(3L);
        when(taskMapper.countLeaseAnomalies(any())).thenReturn(1L);

        AgentSchedulerStatusRespVO result = schedulerStatusService.getStatus();

        assertThat(result.getQueueLength()).isEqualTo(12L);
        assertThat(result.getRunningCount()).isEqualTo(3L);
        assertThat(result.getLeaseAnomalyCount()).isEqualTo(1L);
        assertThat(result.getCheckedAt()).isBetween(
                before.minusSeconds(2), LocalDateTime.now().plusSeconds(2));

        verify(taskMapper).countPendingTasks();
        verify(taskMapper).countRunningTasks();
        verify(taskMapper).countLeaseAnomalies(any(LocalDateTime.class));
    }

}
