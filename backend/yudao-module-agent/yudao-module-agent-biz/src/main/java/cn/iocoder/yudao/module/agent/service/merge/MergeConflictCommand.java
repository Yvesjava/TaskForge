package cn.iocoder.yudao.module.agent.service.merge;

import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeOptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 冲突转人工与继续合并的命令。
 *
 * <p>合并编排入口携带任务主键、幂等键、各仓预检目标与合并选项。预检阶段先确认
 * 所有仓库可无冲突合并，再复用预检解析出的 MR/PR 引用执行真正合并，从而保证
 * 任一仓冲突时不会发生部分合并。</p>
 *
 * @param taskId         任务主键 ID
 * @param idempotencyKey 回调/请求幂等键
 * @param targets        各仓库预检目标（每个仓库仅出现一次）
 * @param mergeOptions   预检通过后执行合并时使用的统一选项
 * @author TaskForge
 */
public record MergeConflictCommand(Long taskId, String idempotencyKey,
                                   List<MergePrecheckTarget> targets,
                                   GitMergeOptions mergeOptions) {

    public MergeConflictCommand {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey 不能为空");
        }
        Objects.requireNonNull(mergeOptions, "mergeOptions 不能为空");
        Objects.requireNonNull(targets, "targets 不能为空");
        List<MergePrecheckTarget> copy = new ArrayList<>(targets);
        for (MergePrecheckTarget target : copy) {
            Objects.requireNonNull(target, "targets 不能包含空元素");
        }
        targets = List.copyOf(copy);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("合并预检目标不能为空");
        }
        Set<String> repoKeys = new HashSet<>();
        for (MergePrecheckTarget target : targets) {
            if (!repoKeys.add(target.repoKey())) {
                throw new IllegalArgumentException("合并预检目标存在重复仓库：" + target.repoKey());
            }
        }
    }

    public static MergeConflictCommand of(Long taskId, String idempotencyKey,
                                         List<MergePrecheckTarget> targets,
                                         GitMergeOptions mergeOptions) {
        return new MergeConflictCommand(taskId, idempotencyKey, targets, mergeOptions);
    }

}
