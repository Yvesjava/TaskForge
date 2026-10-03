package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskResetRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_CANNOT_RESET_NOT_PAUSED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_NOT_FOUND;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_SUBMIT_IDEMPOTENCY_KEY_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_SUBMIT_IDEMPOTENCY_KEY_REQUIRED;

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

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{16,128}$");

    @Resource
    private AgentTaskMapper taskMapper;

    @Resource
    private AgentTaskOperationLogMapper operationLogMapper;

    @Resource
    private AgentTaskStateMachine stateMachine;

    @Resource
    private ResetCleanupDelegate cleanupDelegate;

    @Override
    public AgentTaskResetRespVO reset(Long taskId, boolean keepPaused, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);

        // 幂等重放：同一任务 + 同一幂等键已执行过，直接返回当前结果
        AgentTaskOperationLogDO existingLog = operationLogMapper.selectByTaskIdAndRequestKey(taskId, idempotencyKey);
        if (existingLog != null) {
            AgentTaskDO existingTask = taskMapper.selectById(taskId);
            if (existingTask != null) {
                return buildResponse(existingTask, existingTask.getStatus());
            }
        }

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
                .requestIdempotencyKey(idempotencyKey)
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

        return buildResponse(task, finalStatus.getValue());
    }

    private AgentTaskResetRespVO buildResponse(AgentTaskDO task, String status) {
        AgentTaskResetRespVO response = new AgentTaskResetRespVO();
        response.setTaskId(task.getId());
        response.setTaskNo(task.getTaskNo());
        response.setStatus(status);
        return response;
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw exception(TASK_SUBMIT_IDEMPOTENCY_KEY_REQUIRED);
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw exception(TASK_SUBMIT_IDEMPOTENCY_KEY_INVALID);
        }
    }

}
