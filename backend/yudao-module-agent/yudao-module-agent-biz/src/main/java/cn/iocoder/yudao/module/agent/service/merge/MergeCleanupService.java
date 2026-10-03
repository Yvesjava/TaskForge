package cn.iocoder.yudao.module.agent.service.merge;

import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskProjectMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskStateMachine;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskTransitionCommand;
import cn.iocoder.yudao.module.agent.service.workspace.BareRepoManager;
import cn.iocoder.yudao.module.agent.service.workspace.TaskBranchManager;
import cn.iocoder.yudao.module.agent.service.workspace.WorktreeManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.MERGE_CLEANUP_REPOS_NOT_MERGED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.MERGE_TASK_STATE_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_NOT_FOUND;

/**
 * 合并完成后资源清理服务（TASK-MERGE-05）。
 *
 * <p>全仓合并成功后任务停留在 {@code ACCEPTED}，本服务负责回收任务创建的
 * 聚合工作区、特性分支与裸仓库缓存；只有三类资源全部清理完成后才通过
 * {@code MERGE_PASS} 把任务推进到 {@code COMPLETED}。</p>
 *
 * <p>清理必须幂等：资源已不存在时直接跳过，重复回调不会产生二次副作用。
 * 任一步清理失败都会保留 {@code ACCEPTED}，由补偿任务继续重试，避免把任务
 * 伪装成已完成。</p>
 *
 * @author TaskForge
 */
@Service
public class MergeCleanupService {

    private final AgentTaskMapper taskMapper;
    private final AgentTaskProjectMapper taskProjectMapper;
    private final AgentTaskStateMachine stateMachine;
    private final WorktreeManager worktreeManager;
    private final TaskBranchManager taskBranchManager;
    private final BareRepoManager bareRepoManager;

    public MergeCleanupService(AgentTaskMapper taskMapper,
                               AgentTaskProjectMapper taskProjectMapper,
                               AgentTaskStateMachine stateMachine,
                               WorktreeManager worktreeManager,
                               TaskBranchManager taskBranchManager,
                               BareRepoManager bareRepoManager) {
        this.taskMapper = Objects.requireNonNull(taskMapper, "taskMapper 不能为空");
        this.taskProjectMapper = Objects.requireNonNull(taskProjectMapper, "taskProjectMapper 不能为空");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine 不能为空");
        this.worktreeManager = Objects.requireNonNull(worktreeManager, "worktreeManager 不能为空");
        this.taskBranchManager = Objects.requireNonNull(taskBranchManager, "taskBranchManager 不能为空");
        this.bareRepoManager = Objects.requireNonNull(bareRepoManager, "bareRepoManager 不能为空");
    }

    /**
     * 执行合并收尾清理。
     *
     * @param command 清理命令
     * @return 清理完成后的任务状态（{@code COMPLETED}）
     */
    public AgentTaskStatus cleanup(MergeCleanupCommand command) {
        Objects.requireNonNull(command, "command 不能为空");

        // 1. 读取真实状态，避免信任调用方传入状态；已 COMPLETED 直接幂等返回
        AgentTaskDO task = taskMapper.selectById(command.taskId());
        if (task == null) {
            throw exception(TASK_NOT_FOUND);
        }
        String currentStatus = task.getStatus();
        if (AgentTaskStatus.COMPLETED.getValue().equals(currentStatus)) {
            return AgentTaskStatus.COMPLETED;
        }
        if (!AgentTaskStatus.ACCEPTED.getValue().equals(currentStatus)) {
            throw exception(MERGE_TASK_STATE_INVALID, currentStatus);
        }

        // 2. 仅在全仓都已合并时才允许收尾，防止提前删除资源并伪装成完成
        List<AgentTaskProjectDO> projects = taskProjectMapper.selectListByTaskId(command.taskId());
        boolean allMerged = projects.stream().allMatch(
                project -> MergeRepoResult.MERGE_STATUS_MERGED.equals(project.getMergeStatus()));
        if (!allMerged) {
            throw exception(MERGE_CLEANUP_REPOS_NOT_MERGED);
        }

        // 3. 依次回收聚合工作区、特性分支与裸仓库缓存
        worktreeManager.destroyCompositeWorkspaceIfPresent(task.getTaskNo());

        if (task.getTargetBranch() != null && !task.getTargetBranch().isBlank()) {
            for (AgentTaskProjectDO project : projects) {
                taskBranchManager.deleteFeatureBranch(project.getProjectCode(), task.getTargetBranch());
            }
        }

        for (AgentTaskProjectDO project : projects) {
            bareRepoManager.deleteRepositoryIfPresent(project.getProjectCode());
        }

        // 4. 全部资源清理完成后才进入 COMPLETED
        return stateMachine.transition(AgentTaskTransitionCommand.builder()
                .taskId(task.getId())
                .taskNo(task.getTaskNo())
                .action(AgentTaskAction.MERGE_PASS)
                .fromStatus(AgentTaskStatus.ACCEPTED)
                .docVersion(task.getDocVersion())
                .requestIdempotencyKey(command.idempotencyKey())
                .build());
    }

}
