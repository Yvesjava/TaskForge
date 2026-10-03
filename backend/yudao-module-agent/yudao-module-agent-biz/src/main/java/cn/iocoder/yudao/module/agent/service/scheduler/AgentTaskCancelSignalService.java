package cn.iocoder.yudao.module.agent.service.scheduler;

/**
 * 任务取消信号服务
 *
 * <p>维护 Redis 中的 {@code agent:task:cancel:{taskId}} 键，值为触发取消的执行代次。
 * 控制面在任务取消或失租恢复时写入信号，Worker 在执行提交、推送或结果写回前轮询
 * 该信号，命中即立即停止写入。Redis 只负责快速广播，MySQL 仍是任务状态的最终
 * 事实来源。</p>
 *
 * @author TaskForge
 */
public interface AgentTaskCancelSignalService {

    /**
     * 发布取消信号。
     *
     * @param taskId     任务主键 ID
     * @param generation 触发取消的执行代次
     */
    void publish(Long taskId, Long generation);

    /**
     * 判断指定执行代次是否已被取消。
     *
     * <p>当取消信号存在且信号代次不低于当前代次时返回 {@code true}。使用“不低于”
     * 比较可保证旧 Worker 立即停止，而更高代次的新 Worker 不受旧信号影响。</p>
     *
     * @param taskId     任务主键 ID
     * @param generation 当前执行代次
     * @return {@code true} 表示当前执行代次已被取消
     */
    boolean isCanceled(Long taskId, Long generation);

    /**
     * 清除取消信号（任务重新抢占或执行结束时调用）。
     *
     * @param taskId 任务主键 ID
     */
    void clear(Long taskId);

}
