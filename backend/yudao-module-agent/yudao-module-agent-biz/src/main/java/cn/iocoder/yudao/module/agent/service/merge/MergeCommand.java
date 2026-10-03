package cn.iocoder.yudao.module.agent.service.merge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 多仓合并命令。
 *
 * <p>合并回调/编排入口通过本命令携带任务、幂等键与各仓合并目标。幂等键沿用任务
 * 操作审计表的 {@code request_idempotency_key} 约定，重复回调时返回首次结果。</p>
 *
 * @param taskId         任务主键 ID
 * @param idempotencyKey 回调/请求幂等键
 * @param targets        各仓库合并目标（每个仓库仅出现一次）
 * @author TaskForge
 */
public record MergeCommand(Long taskId, String idempotencyKey, List<MergeTarget> targets) {

    public MergeCommand {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey 不能为空");
        }
        Objects.requireNonNull(targets, "targets 不能为空");
        List<MergeTarget> copy = new ArrayList<>(targets);
        for (MergeTarget target : copy) {
            Objects.requireNonNull(target, "targets 不能包含空元素");
        }
        targets = List.copyOf(copy);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("合并目标不能为空");
        }
        Set<String> repoKeys = new HashSet<>();
        for (MergeTarget target : targets) {
            if (!repoKeys.add(target.repoKey())) {
                throw new IllegalArgumentException("合并目标存在重复仓库：" + target.repoKey());
            }
        }
    }

    public static MergeCommand of(Long taskId, String idempotencyKey, List<MergeTarget> targets) {
        return new MergeCommand(taskId, idempotencyKey, targets);
    }

}
