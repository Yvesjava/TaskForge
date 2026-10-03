package cn.iocoder.yudao.module.agent.service.scheduler;

/**
 * 租约过期扫描与失败恢复服务
 *
 * <p>周期扫描 {@code RUNNING AND lease_until < NOW} 的任务，将其恢复为 {@code FAILED}
 * 并递增执行代次、清空 Worker 与租约信息，避免旧 Worker 在失租后仍写回结果。</p>
 *
 * @author TaskForge
 */
public interface AgentTaskLeaseRecoveryService {

    /**
     * 执行一次有界批次的租约过期恢复。
     *
     * @return 本次恢复的任务数量
     */
    int recoverExpiredLeases();

}
