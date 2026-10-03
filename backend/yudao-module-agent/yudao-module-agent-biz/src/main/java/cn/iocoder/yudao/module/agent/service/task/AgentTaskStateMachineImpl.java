package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_STATUS_TRANSITION_NOT_ALLOWED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_STATUS_TRANSITION_TARGET_REQUIRED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_STATUS_UPDATE_CONFLICT;

/**
 * 任务状态机统一服务入口实现
 *
 * <p>校验合法转换矩阵后，按动作分发到带状态前置条件的条件更新 SQL；任何未列出的
 * 转换或条件更新失败（影响行数为 0）都返回冲突错误，且不写审计。</p>
 *
 * @author TaskForge
 */
@Service
public class AgentTaskStateMachineImpl implements AgentTaskStateMachine {

    @Resource
    private AgentTaskMapper taskMapper;

    @Resource
    private AgentTaskOperationLogMapper operationLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentTaskStatus transition(AgentTaskTransitionCommand command) {
        AgentTaskStatus from = command.getFromStatus();
        AgentTaskAction action = command.getAction();
        AgentTaskStatus target = resolveTarget(from, action, command.getTargetStatus());

        int updated = applyConditionalUpdate(command, target);
        if (updated == 0) {
            throw exception(TASK_STATUS_UPDATE_CONFLICT, command.getTaskId(), from.getValue(), action.getValue());
        }

        recordOperationLog(command, from, target);
        return target;
    }

    private AgentTaskStatus resolveTarget(AgentTaskStatus from, AgentTaskAction action, AgentTaskStatus explicitTarget) {
        Set<AgentTaskStatus> legalTargets = AgentTaskTransitionMatrix.legalTargets(from, action);
        if (legalTargets.isEmpty()) {
            throw exception(TASK_STATUS_TRANSITION_NOT_ALLOWED, from == null ? null : from.getValue(), action.getValue());
        }
        if (explicitTarget != null) {
            if (!legalTargets.contains(explicitTarget)) {
                throw exception(TASK_STATUS_TRANSITION_NOT_ALLOWED, from.getValue(), action.getValue());
            }
            return explicitTarget;
        }
        if (legalTargets.size() != 1) {
            throw exception(TASK_STATUS_TRANSITION_TARGET_REQUIRED, action.getValue());
        }
        return legalTargets.iterator().next();
    }

    private int applyConditionalUpdate(AgentTaskTransitionCommand command, AgentTaskStatus target) {
        Long id = command.getTaskId();
        return switch (command.getAction()) {
            case PAUSE -> taskMapper.pauseIfPending(id);
            case RESUME -> taskMapper.resumeIfPaused(id);
            case CANCEL -> taskMapper.cancelIfPendingOrPaused(id, command.getCancelReason());
            case EDIT -> taskMapper.updateDocumentIfVersionMatches(id, command.getExpectedDocVersion(),
                    command.getTaskDoc(), command.getTimeoutMinutes(), command.getPriority(), command.getDependsOnTaskId());
            case RESET -> taskMapper.markResettingIfPaused(id);
            case CLAIM -> taskMapper.markTaskRunning(id, command.getWorkerId(), command.getLeaseUntil(),
                    command.getGeneration());
            case SELF_VERIFY_PASS -> taskMapper.markSelfVerifiedIfRunning(id, command.getWorkerId(), command.getGeneration(),
                    command.getExecutionLog(), command.getRetryTimes(), command.getCostMs(),
                    command.getDiffStat(), command.getWorkspacePath());
            case TIMEOUT, ERROR -> taskMapper.markFailedIfRunning(id, command.getWorkerId(), command.getGeneration(),
                    command.getExecutionLog(), command.getRetryTimes(), command.getCostMs(), command.getDiffStat());
            case HEARTBEAT_EXPIRED -> taskMapper.markLeaseExpiredFailed(id, command.getGeneration());
            case ACCEPT -> taskMapper.acceptIfWaitingAcceptance(id);
            case REJECT -> taskMapper.rejectIfWaitingAcceptance(id);
            case MERGE_PASS -> taskMapper.completeIfAccepted(id);
            case MERGE_CONFLICT -> taskMapper.markMergeConflictIfAccepted(id);
            case RE_ENQUEUE -> taskMapper.reEnqueueIfEnded(id);
            case DELETE -> taskMapper.softDeleteIfCanceled(id);
            case CLEANUP_PASS -> taskMapper.finishReset(id, target.getValue());
        };
    }

    private void recordOperationLog(AgentTaskTransitionCommand command, AgentTaskStatus from, AgentTaskStatus target) {
        AgentTaskOperationLogDO log = AgentTaskOperationLogDO.builder()
                .taskId(command.getTaskId())
                .action(command.getAction().getValue())
                .fromStatus(from == null ? null : from.getValue())
                .toStatus(target.getValue())
                .docVersion(resolveLoggedDocVersion(command))
                .feedback(resolveFeedback(command))
                .requestIdempotencyKey(resolveIdempotencyKey(command))
                .operatorId(resolveOperatorId(command))
                .operatorName(resolveOperatorName(command))
                .payload(command.getPayload())
                .createTime(LocalDateTime.now())
                .build();
        operationLogMapper.insert(log);
    }

    private Integer resolveLoggedDocVersion(AgentTaskTransitionCommand command) {
        if (command.getDocVersion() != null) {
            return command.getDocVersion();
        }
        if (command.getAction() != AgentTaskAction.EDIT || command.getExpectedDocVersion() == null) {
            return null;
        }
        return command.getExpectedDocVersion() + 1;
    }

    private String resolveFeedback(AgentTaskTransitionCommand command) {
        if (command.getFeedback() != null) {
            return command.getFeedback();
        }
        return command.getCancelReason();
    }

    private String resolveIdempotencyKey(AgentTaskTransitionCommand command) {
        if (command.getRequestIdempotencyKey() != null) {
            return command.getRequestIdempotencyKey();
        }
        return "internal:" + command.getAction().getValue() + ":" + command.getTaskId() + ":" + UUID.randomUUID();
    }

    private Long resolveOperatorId(AgentTaskTransitionCommand command) {
        return command.getOperatorId() != null ? command.getOperatorId() : SecurityFrameworkUtils.getLoginUserId();
    }

    private String resolveOperatorName(AgentTaskTransitionCommand command) {
        return command.getOperatorName() != null ? command.getOperatorName() : SecurityFrameworkUtils.getLoginUserNickname();
    }

}
