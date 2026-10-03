package cn.iocoder.yudao.module.agent.service.merge;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskProjectMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiOperator;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiOperatorRegistry;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeResult;
import cn.iocoder.yudao.module.agent.framework.secret.SecretRedactor;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskStateMachine;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskTransitionCommand;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.MERGE_TASK_STATE_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_NOT_FOUND;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_STATUS_UPDATE_CONFLICT;

/**
 * 多仓合并编排：Fast-Forward/Squash 合并与结果回写。
 *
 * <p>负责把预检通过的各仓 MR/PR 真正合并，逐仓回写 {@code merge_status} 与
 * {@code commit_hash}，并保证任务状态与各仓结果一致：
 * <ul>
 *   <li>全仓合并成功：任务保持 {@code ACCEPTED}（清理完成后由 TASK-MERGE-05 置为
 *       {@code COMPLETED}）；</li>
 *   <li>任一仓失败：任务经 {@code MERGE_CONFLICT} 进入
 *       {@code MERGE_CONFLICT_PENDING_MANUAL}。</li>
 * </ul>
 * 同一幂等键的重复回调直接返回首次结果，已合并成功的仓库也不会被重复合并。</p>
 *
 * @author TaskForge
 */
@Service
public class MergeService {

    private final GitApiOperatorRegistry operatorRegistry;
    private final AgentTaskMapper taskMapper;
    private final AgentTaskProjectMapper taskProjectMapper;
    private final AgentTaskOperationLogMapper operationLogMapper;
    private final AgentTaskStateMachine stateMachine;

    public MergeService(GitApiOperatorRegistry operatorRegistry,
                        AgentTaskMapper taskMapper,
                        AgentTaskProjectMapper taskProjectMapper,
                        AgentTaskOperationLogMapper operationLogMapper,
                        AgentTaskStateMachine stateMachine) {
        this.operatorRegistry = Objects.requireNonNull(operatorRegistry, "operatorRegistry 不能为空");
        this.taskMapper = Objects.requireNonNull(taskMapper, "taskMapper 不能为空");
        this.taskProjectMapper = Objects.requireNonNull(taskProjectMapper, "taskProjectMapper 不能为空");
        this.operationLogMapper = Objects.requireNonNull(operationLogMapper, "operationLogMapper 不能为空");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine 不能为空");
    }

    public MergeOutcome merge(MergeCommand command) {
        Objects.requireNonNull(command, "command 不能为空");

        // 1. 幂等重放：同一任务 + 同一幂等键已经执行过合并动作（冲突或后续清理完成）
        AgentTaskOperationLogDO existingLog = operationLogMapper.selectByTaskIdAndRequestKey(
                command.taskId(), command.idempotencyKey());
        if (existingLog != null && isMergeAction(existingLog.getAction())) {
            return replay(command.taskId(), existingLog);
        }

        // 2. 读取任务真实状态，避免信任回调传入的状态
        AgentTaskDO task = taskMapper.selectById(command.taskId());
        if (task == null) {
            throw exception(TASK_NOT_FOUND);
        }
        String currentStatus = task.getStatus();
        if (AgentTaskStatus.COMPLETED.getValue().equals(currentStatus)
                || AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL.getValue().equals(currentStatus)) {
            return replayFromStatus(command.taskId(), currentStatus);
        }
        if (!AgentTaskStatus.ACCEPTED.getValue().equals(currentStatus)) {
            throw exception(MERGE_TASK_STATE_INVALID, currentStatus);
        }

        // 3. 已回写成功的仓库不重复合并，保证逐仓幂等
        Map<String, AgentTaskProjectDO> persistedByCode = taskProjectMapper.selectListByTaskId(command.taskId())
                .stream()
                .collect(Collectors.toMap(AgentTaskProjectDO::getProjectCode, Function.identity(), (a, b) -> a));

        List<MergeRepoResult> results = new ArrayList<>(command.targets().size());
        for (MergeTarget target : command.targets()) {
            AgentTaskProjectDO persisted = persistedByCode.get(target.repoKey());
            if (persisted != null && MergeRepoResult.MERGE_STATUS_MERGED.equals(persisted.getMergeStatus())) {
                results.add(MergeRepoResult.merged(target.repoKey(), persisted.getCommitHash(), ""));
                continue;
            }
            MergeRepoResult result = mergeOne(target);
            results.add(result);
            taskProjectMapper.updateMergeResult(command.taskId(), result.repoKey(),
                    result.mergeStatus(), result.commitSha());
        }

        // 4. 全仓成功才保持 ACCEPTED；任一仓失败则进入 MERGE_CONFLICT_PENDING_MANUAL
        boolean allMerged = results.stream().allMatch(MergeRepoResult::merged);
        if (allMerged) {
            return MergeOutcome.success(AgentTaskStatus.ACCEPTED.getValue(), results);
        }
        AgentTaskStatus finalStatus = transitionTask(task, AgentTaskAction.MERGE_CONFLICT, command.idempotencyKey());
        return MergeOutcome.conflict(finalStatus.getValue(), results);
    }

    private MergeRepoResult mergeOne(MergeTarget target) {
        try {
            GitApiOperator operator = operatorRegistry.get(target.repository().platform());
            GitMergeResult result = operator.merge(target.repository(), target.credentials(), target.ref(), target.options());
            if (result.merged()) {
                return MergeRepoResult.merged(target.repoKey(), result.commitSha(), result.webUrl());
            }
            return MergeRepoResult.failed(target.repoKey(), MergeRepoResult.MERGE_STATUS_FAILED,
                    result.webUrl(), result.message());
        } catch (RuntimeException ex) {
            return MergeRepoResult.failed(target.repoKey(), MergeRepoResult.MERGE_STATUS_FAILED, "", reasonOf(ex));
        }
    }

    private AgentTaskStatus transitionTask(AgentTaskDO task, AgentTaskAction action, String idempotencyKey) {
        AgentTaskTransitionCommand command = AgentTaskTransitionCommand.builder()
                .taskId(task.getId())
                .taskNo(task.getTaskNo())
                .action(action)
                .fromStatus(AgentTaskStatus.valueOfCode(task.getStatus()))
                .docVersion(task.getDocVersion())
                .requestIdempotencyKey(idempotencyKey)
                .build();
        try {
            return stateMachine.transition(command);
        } catch (ServiceException ex) {
            if (ex.getCode() != TASK_STATUS_UPDATE_CONFLICT.getCode()) {
                throw ex;
            }
            // 并发重复回调时，另一方已完成状态转换；回读胜者结果，避免向调用方报错
            AgentTaskOperationLogDO winner = operationLogMapper.selectByTaskIdAndRequestKey(task.getId(), idempotencyKey);
            if (winner != null) {
                AgentTaskStatus winnerStatus = AgentTaskStatus.valueOfCode(winner.getToStatus());
                if (winnerStatus != null) {
                    return winnerStatus;
                }
            }
            AgentTaskDO current = taskMapper.selectById(task.getId());
            AgentTaskStatus currentStatus = current == null ? null : AgentTaskStatus.valueOfCode(current.getStatus());
            return currentStatus != null ? currentStatus : AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL;
        }
    }

    private MergeOutcome replay(Long taskId, AgentTaskOperationLogDO log) {
        AgentTaskDO task = taskMapper.selectById(taskId);
        String status = task == null ? log.getToStatus() : task.getStatus();
        return outcomeFromPersisted(taskId, status);
    }

    private MergeOutcome replayFromStatus(Long taskId, String status) {
        return outcomeFromPersisted(taskId, status);
    }

    private MergeOutcome outcomeFromPersisted(Long taskId, String status) {
        List<MergeRepoResult> results = taskProjectMapper.selectListByTaskId(taskId).stream()
                .map(this::toRepoResult)
                .toList();
        boolean allMerged = results.stream().allMatch(MergeRepoResult::merged);
        boolean success = AgentTaskStatus.COMPLETED.getValue().equals(status)
                || (AgentTaskStatus.ACCEPTED.getValue().equals(status) && allMerged);
        return new MergeOutcome(status, success, results);
    }

    private MergeRepoResult toRepoResult(AgentTaskProjectDO project) {
        if (MergeRepoResult.MERGE_STATUS_MERGED.equals(project.getMergeStatus())) {
            return MergeRepoResult.merged(project.getProjectCode(), project.getCommitHash(), "");
        }
        String mergeStatus = project.getMergeStatus() == null ? "" : project.getMergeStatus();
        return MergeRepoResult.failed(project.getProjectCode(), mergeStatus, "", "");
    }

    private boolean isMergeAction(String action) {
        return AgentTaskAction.MERGE_PASS.getValue().equals(action)
                || AgentTaskAction.MERGE_CONFLICT.getValue().equals(action);
    }

    private String reasonOf(Exception ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName() : ex.getMessage();
        return SecretRedactor.redact(message);
    }

}
