package cn.iocoder.yudao.module.agent.service.scheduler;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.framework.scheduler.AgentSchedulerProperties;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskStateMachine;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskTransitionCommand;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 租约过期扫描与失败恢复服务实现
 *
 * <p>在独立短事务内按 {@code FOR UPDATE SKIP LOCKED} 扫描租约过期的 {@code RUNNING}
 * 任务，对每个任务通过状态机执行 {@code RUNNING -> FAILED}（动作 {@code HEARTBEAT_EXPIRED}），
 * 递增执行代次并清空 Worker/租约，随后释放 Redis 租约与心跳键。递增后的执行代次使
 * 旧 Worker 的写回条件失配，无法覆盖新结果。</p>
 *
 * @author TaskForge
 */
@Service
@Slf4j
public class AgentTaskLeaseRecoveryServiceImpl implements AgentTaskLeaseRecoveryService {

    @Resource
    private AgentTaskMapper taskMapper;

    @Resource
    private AgentTaskStateMachine stateMachine;

    @Resource
    private AgentTaskLeaseService taskLeaseService;

    @Resource
    private AgentSchedulerProperties schedulerProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recoverExpiredLeases() {
        // 调度器按租户忽略模式扫描所有租户的运行中任务，与抢占/状态查询保持一致
        return TenantUtils.executeIgnore(this::doRecoverExpiredLeases);
    }

    private int doRecoverExpiredLeases() {
        List<AgentTaskDO> expiredTasks = taskMapper.selectExpiredRunningTasks(
                schedulerProperties.getRecoveryBatchSize());
        for (AgentTaskDO task : expiredTasks) {
            long currentGeneration = task.getExecutionGeneration() == null ? 0L : task.getExecutionGeneration();
            long nextGeneration = currentGeneration + 1;

            stateMachine.transition(AgentTaskTransitionCommand.builder()
                    .taskId(task.getId())
                    .taskNo(task.getTaskNo())
                    .action(AgentTaskAction.HEARTBEAT_EXPIRED)
                    .fromStatus(AgentTaskStatus.RUNNING)
                    .generation(nextGeneration)
                    .build());

            taskLeaseService.release(task.getId());
            log.info("[LeaseRecovery] 租约过期任务已恢复为 FAILED taskId={}, taskNo={}, workerId={}, generation={} -> {}",
                    task.getId(), task.getTaskNo(), task.getWorkerId(), currentGeneration, nextGeneration);
        }
        return expiredTasks.size();
    }

}
