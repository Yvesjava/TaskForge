package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskResetRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_CANNOT_RESET_NOT_PAUSED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_NOT_FOUND;

/**
 * 任务重置服务实现
 *
 * <p>重置流程拆成三个边界，避免数据库事务包裹外部 Git/磁盘操作：</p>
 * <ol>
 *   <li>状态抢占：{@code PAUSED -> RESETTING}（条件更新 + 操作审计）</li>
 *   <li>外部清理：通过 {@link ResetCleanupDelegate} 幂等回收工作区与分支</li>
 *   <li>元数据清理：{@code RESETTING -> PENDING/PAUSED}，清空执行痕迹</li>
 * </ol>
 *
 * <p>任一步外部清理失败都会保留 {@code RESETTING}，由补偿任务继续清理，
 * 不会直接把任务伪装成 {@code PENDING}。</p>
 *
 * @author TaskForge
 */
@Service
public class AgentTaskResetServiceImpl implements AgentTaskResetService {

    @Resource
    private AgentTaskMapper taskMapper;

    @Resource
    private AgentTaskStateMachine stateMachine;

    @Resource
    private ResetCleanupDelegate cleanupDelegate;

    @Override
    public AgentTaskResetRespVO reset(Long taskId, boolean keepPaused) {
        AgentTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw exception(TASK_NOT_FOUND);
        }
        if (!AgentTaskStatus.PAUSED.getValue().equals(task.getStatus())) {
            throw exception(TASK_CANNOT_RESET_NOT_PAUSED);
        }

        // 1. 状态抢占：PAUSED -> RESETTING，写入 RESET 审计
        stateMachine.transition(AgentTaskTransitionCommand.builder()
                .taskId(taskId)
                .taskNo(task.getTaskNo())
                .action(AgentTaskAction.RESET)
                .fromStatus(AgentTaskStatus.PAUSED)
                .build());

        // 2. 事务外执行幂等外部清理；失败保留 RESETTING，由补偿任务继续
        cleanupDelegate.cleanup(new ResetCleanupContext(taskId, task.getTaskNo(), task.getTargetBranch()));

        // 3. 元数据清理：RESETTING -> PENDING/PAUSED，写入 CLEANUP_PASS 审计
        AgentTaskStatus finalStatus = keepPaused ? AgentTaskStatus.PAUSED : AgentTaskStatus.PENDING;
        stateMachine.transition(AgentTaskTransitionCommand.builder()
                .taskId(taskId)
                .taskNo(task.getTaskNo())
                .action(AgentTaskAction.CLEANUP_PASS)
                .fromStatus(AgentTaskStatus.RESETTING)
                .targetStatus(finalStatus)
                .build());

        AgentTaskResetRespVO response = new AgentTaskResetRespVO();
        response.setTaskId(taskId);
        response.setTaskNo(task.getTaskNo());
        response.setStatus(finalStatus.getValue());
        return response;
    }

}
