package cn.iocoder.yudao.module.agent.service.scheduler;

import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.framework.scheduler.AgentSchedulerProperties;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskStateMachine;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskTransitionCommand;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 任务短事务抢占服务实现
 *
 * <p>“捞取并抢占”必须在同一个独立短事务内完成：先按优先级、创建时间升序用
 * {@code FOR UPDATE SKIP LOCKED} 锁定下一个 {@code PENDING} 任务，再通过状态机执行
 * {@code PENDING -> RUNNING} 条件更新并写入审计。事务提交后才返回，调用方再投递异步执行，
 * 不在持有数据库行锁时启动 Git 或 Codex。</p>
 *
 * @author TaskForge
 */
@Service
public class AgentTaskClaimServiceImpl implements AgentTaskClaimService {

    @Resource
    private AgentTaskMapper taskMapper;

    @Resource
    private AgentTaskStateMachine stateMachine;

    @Resource
    private WorkerIdentity workerIdentity;

    @Resource
    private AgentSchedulerProperties schedulerProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Optional<AgentTaskDO> claimNextTask() {
        AgentTaskDO task = taskMapper.selectNextPendingTaskForUpdate();
        if (task == null) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        long generation = task.getExecutionGeneration() + 1;
        LocalDateTime leaseUntil = now.plusMinutes(schedulerProperties.getLeaseMinutes());
        String workerId = workerIdentity.id();

        AgentTaskTransitionCommand command = AgentTaskTransitionCommand.builder()
                .taskId(task.getId())
                .taskNo(task.getTaskNo())
                .action(AgentTaskAction.CLAIM)
                .fromStatus(AgentTaskStatus.PENDING)
                .workerId(workerId)
                .leaseUntil(leaseUntil)
                .generation(generation)
                .build();
        stateMachine.transition(command);

        // 返回给调度器的任务快照携带本次执行代次与租约持有者，供后续异步执行校验使用
        task.setStatus(AgentTaskStatus.RUNNING.getValue());
        task.setWorkerId(workerId);
        task.setLeaseUntil(leaseUntil);
        task.setExecutionGeneration(generation);
        return Optional.of(task);
    }

}
