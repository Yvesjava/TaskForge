package cn.iocoder.yudao.module.agent.service.merge;

import java.util.List;

/**
 * 多仓合并编排结果。
 *
 * <p>全仓合并成功时任务保持 {@code ACCEPTED}，等待 TASK-MERGE-05 完成资源清理后
 * 再进入 {@code COMPLETED}；任一仓合并失败时任务进入
 * {@code MERGE_CONFLICT_PENDING_MANUAL}，为 TASK-MERGE-04 的重试/继续合并保留可恢复路径。</p>
 *
 * @param taskStatus  最终任务状态
 * @param success     是否全部仓库合并成功
 * @param repoResults 各仓库合并结果，顺序与合并输入一致
 * @author TaskForge
 */
public record MergeOutcome(String taskStatus, boolean success, List<MergeRepoResult> repoResults) {

    public MergeOutcome {
        if (taskStatus == null || taskStatus.isBlank()) {
            throw new IllegalArgumentException("taskStatus 不能为空");
        }
        repoResults = List.copyOf(repoResults);
    }

    public static MergeOutcome success(String taskStatus, List<MergeRepoResult> repoResults) {
        return new MergeOutcome(taskStatus, true, repoResults);
    }

    public static MergeOutcome conflict(String taskStatus, List<MergeRepoResult> repoResults) {
        return new MergeOutcome(taskStatus, false, repoResults);
    }

}
