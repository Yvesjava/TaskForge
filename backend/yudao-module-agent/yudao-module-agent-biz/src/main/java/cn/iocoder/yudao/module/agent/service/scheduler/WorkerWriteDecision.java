package cn.iocoder.yudao.module.agent.service.scheduler;

/**
 * Worker 停止写入判定结果
 *
 * <p>承载提交、推送与结果写回三项动作的允许状态。当任务被取消或失租时，三项
 * 动作全部禁止；只有仍持有当前租约且未收到取消信号时才全部放行。</p>
 *
 * @param commitAllowed   是否允许提交
 * @param pushAllowed     是否允许推送
 * @param writeBackAllowed 是否允许写回执行结果
 * @author TaskForge
 */
public record WorkerWriteDecision(
        boolean commitAllowed,
        boolean pushAllowed,
        boolean writeBackAllowed) {

    /**
     * 是否允许继续任何写入动作（提交、推送、结果写回）。
     */
    public boolean isWriteAllowed() {
        return commitAllowed && pushAllowed && writeBackAllowed;
    }

}
