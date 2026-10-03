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
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeRequestRef;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskStateMachine;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskTransitionCommand;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.MERGE_TASK_STATE_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_NOT_FOUND;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_STATUS_UPDATE_CONFLICT;

/**
 * 冲突转人工与继续合并编排。
 *
 * <p>在 {@link MergePrecheckService} 之上实现“接受后先预检、再合并”的闭环：
 * <ul>
 *   <li>任一仓预检冲突/失败：任务经 {@code MERGE_CONFLICT} 进入
 *       {@code MERGE_CONFLICT_PENDING_MANUAL}，回写失败仓状态，且不合并任何仓；</li>
 *   <li>全部预检通过：复用预检解析出的 MR/PR 引用委托 {@link MergeService} 执行合并；</li>
 *   <li>冲突被人工解决后：可经 {@code MERGE_RETRY} 回到 {@code ACCEPTED} 并继续合并，
 *       已合并仓库会被跳过，重复请求幂等重放。</li>
 * </ul>
 *
 * @author TaskForge
 */
@Service
public class MergeConflictService {

    private final MergePrecheckService precheckService;
    private final MergeService mergeService;
    private final AgentTaskMapper taskMapper;
    private final AgentTaskProjectMapper taskProjectMapper;
    private final AgentTaskOperationLogMapper operationLogMapper;
    private final AgentTaskStateMachine stateMachine;

    public MergeConflictService(MergePrecheckService precheckService,
                                MergeService mergeService,
                                AgentTaskMapper taskMapper,
                                AgentTaskProjectMapper taskProjectMapper,
                                AgentTaskOperationLogMapper operationLogMapper,
                                AgentTaskStateMachine stateMachine) {
        this.precheckService = Objects.requireNonNull(precheckService, "precheckService 不能为空");
        this.mergeService = Objects.requireNonNull(mergeService, "mergeService 不能为空");
        this.taskMapper = Objects.requireNonNull(taskMapper, "taskMapper 不能为空");
        this.taskProjectMapper = Objects.requireNonNull(taskProjectMapper, "taskProjectMapper 不能为空");
        this.operationLogMapper = Objects.requireNonNull(operationLogMapper, "operationLogMapper 不能为空");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine 不能为空");
    }

    /**
     * 接受后的合并入口：先对所有仓库预检，冲突则转人工并阻断合并，通过则执行合并。
     */
    public MergeOutcome execute(MergeConflictCommand command) {
        Objects.requireNonNull(command, "command 不能为空");

        AgentTaskOperationLogDO existingLog = operationLogMapper.selectByTaskIdAndRequestKey(
                command.taskId(), command.idempotencyKey());
        if (existingLog != null && isMergeAction(existingLog.getAction())) {
            return replay(command.taskId(), existingLog);
        }

        AgentTaskDO task = requireTask(command.taskId());
        String currentStatus = task.getStatus();
        if (AgentTaskStatus.COMPLETED.getValue().equals(currentStatus)
                || AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL.getValue().equals(currentStatus)) {
            return replayFromStatus(command.taskId(), currentStatus);
        }
        if (!AgentTaskStatus.ACCEPTED.getValue().equals(currentStatus)) {
            throw exception(MERGE_TASK_STATE_INVALID, currentStatus);
        }

        return preflightAndMerge(command, task);
    }

    /**
     * 冲突解决后的重试/继续合并入口：恢复为 {@code ACCEPTED} 后重新预检并合并。
     */
    public MergeOutcome retry(MergeConflictCommand command) {
        Objects.requireNonNull(command, "command 不能为空");

        AgentTaskOperationLogDO existingLog = operationLogMapper.selectByTaskIdAndRequestKey(
                command.taskId(), command.idempotencyKey());
        if (existingLog != null && isMergeAction(existingLog.getAction())) {
            return replay(command.taskId(), existingLog);
        }

        AgentTaskDO task = requireTask(command.taskId());
        String currentStatus = task.getStatus();
        if (AgentTaskStatus.COMPLETED.getValue().equals(currentStatus)) {
            return replayFromStatus(command.taskId(), currentStatus);
        }
        if (AgentTaskStatus.ACCEPTED.getValue().equals(currentStatus)) {
            return preflightAndMerge(command, task);
        }
        if (!AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL.getValue().equals(currentStatus)) {
            throw exception(MERGE_TASK_STATE_INVALID, currentStatus);
        }

        MergePrecheckResult precheck = precheckService.precheck(command.targets());
        if (!precheck.passed()) {
            persistConflictStatuses(command.taskId(), precheck);
            return conflictOutcome(AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL, precheck);
        }

        transitionTask(task, AgentTaskAction.MERGE_RETRY, null);
        return merge(command, precheck);
    }

    private MergeOutcome preflightAndMerge(MergeConflictCommand command, AgentTaskDO task) {
        MergePrecheckResult precheck = precheckService.precheck(command.targets());
        if (!precheck.passed()) {
            AgentTaskStatus finalStatus = transitionTask(task, AgentTaskAction.MERGE_CONFLICT,
                    command.idempotencyKey());
            persistConflictStatuses(command.taskId(), precheck);
            return conflictOutcome(finalStatus, precheck);
        }
        return merge(command, precheck);
    }

    private MergeOutcome merge(MergeConflictCommand command, MergePrecheckResult precheck) {
        List<MergeTarget> targets = toMergeTargets(command, precheck.items());
        return mergeService.merge(MergeCommand.of(command.taskId(), command.idempotencyKey(), targets));
    }

    private List<MergeTarget> toMergeTargets(MergeConflictCommand command,
                                             List<MergePrecheckItemResult> items) {
        List<MergePrecheckTarget> precheckTargets = command.targets();
        List<MergeTarget> targets = new ArrayList<>(precheckTargets.size());
        for (int i = 0; i < precheckTargets.size(); i++) {
            MergePrecheckTarget target = precheckTargets.get(i);
            GitMergeRequestRef ref = items.get(i).ref();
            targets.add(MergeTarget.of(target.repoKey(), target.repository(), target.credentials(),
                    ref, command.mergeOptions()));
        }
        return targets;
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
            AgentTaskOperationLogDO winner = operationLogMapper.selectByTaskIdAndRequestKey(
                    task.getId(), idempotencyKey);
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

    private void persistConflictStatuses(Long taskId, MergePrecheckResult precheck) {
        for (MergePrecheckItemResult item : precheck.items()) {
            if (item.passed()) {
                continue;
            }
            String mergeStatus = item.failure() == MergePrecheckFailure.CONFLICT
                    ? MergeRepoResult.MERGE_STATUS_CONFLICT : MergeRepoResult.MERGE_STATUS_FAILED;
            taskProjectMapper.updateMergeResult(taskId, item.repoKey(), mergeStatus, "");
        }
    }

    private MergeOutcome conflictOutcome(AgentTaskStatus status, MergePrecheckResult precheck) {
        List<MergeRepoResult> results = new ArrayList<>(precheck.items().size());
        for (MergePrecheckItemResult item : precheck.items()) {
            if (item.passed()) {
                results.add(MergeRepoResult.unmerged(item.repoKey()));
            } else if (item.failure() == MergePrecheckFailure.CONFLICT) {
                results.add(MergeRepoResult.conflict(item.repoKey(), webUrl(item.ref()), item.message()));
            } else {
                results.add(MergeRepoResult.failed(item.repoKey(), MergeRepoResult.MERGE_STATUS_FAILED,
                        webUrl(item.ref()), item.message()));
            }
        }
        return MergeOutcome.conflict(status.getValue(), results);
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

    private AgentTaskDO requireTask(Long taskId) {
        AgentTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw exception(TASK_NOT_FOUND);
        }
        return task;
    }

    private boolean isMergeAction(String action) {
        return AgentTaskAction.MERGE_PASS.getValue().equals(action)
                || AgentTaskAction.MERGE_CONFLICT.getValue().equals(action);
    }

    private String webUrl(GitMergeRequestRef ref) {
        return ref == null ? "" : ref.webUrl();
    }

}
