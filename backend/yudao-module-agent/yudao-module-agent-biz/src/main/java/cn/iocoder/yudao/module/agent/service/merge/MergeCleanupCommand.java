package cn.iocoder.yudao.module.agent.service.merge;

/**
 * 合并收尾清理命令。
 *
 * <p>全仓合并成功、任务停留在 {@code ACCEPTED} 后，编排入口通过本命令触发
 * Worktree、分支与裸仓库缓存清理，并在全部清理完成后推进到 {@code COMPLETED}。</p>
 *
 * @param taskId         任务主键 ID
 * @param idempotencyKey 请求幂等键
 * @author TaskForge
 */
public record MergeCleanupCommand(Long taskId, String idempotencyKey) {

    public MergeCleanupCommand {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey 不能为空");
        }
    }

    public static MergeCleanupCommand of(Long taskId, String idempotencyKey) {
        return new MergeCleanupCommand(taskId, idempotencyKey);
    }

}
