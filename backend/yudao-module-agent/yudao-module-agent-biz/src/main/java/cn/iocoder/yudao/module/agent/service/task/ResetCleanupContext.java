package cn.iocoder.yudao.module.agent.service.task;

/**
 * 重置清理上下文
 *
 * <p>在任务从 {@code PAUSED} 抢占为 {@code RESETTING} 之前完成快照，
 * 避免外部清理依赖数据库事务或后续状态变化。清理委托仅使用本快照，
 * 不重新读取任务主表。</p>
 *
 * @param taskId       任务主键 ID
 * @param taskNo       任务唯一编号（用于定位聚合工作区）
 * @param targetBranch 任务特性分支（可能为空，表示无需分支清理）
 * @author TaskForge
 */
public record ResetCleanupContext(Long taskId, String taskNo, String targetBranch) {
}
