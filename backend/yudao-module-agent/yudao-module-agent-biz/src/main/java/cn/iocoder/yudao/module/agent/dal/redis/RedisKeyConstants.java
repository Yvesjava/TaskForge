package cn.iocoder.yudao.module.agent.dal.redis;

/**
 * TaskForge Agent Redis 键常量
 *
 * <p>Redis 只承担租约快速发现、心跳存活判断与取消信号广播，MySQL 仍是任务状态与
 * 租约的最终事实来源。键名与架构文档 §3.2.1 保持一致。</p>
 *
 * @author TaskForge
 */
public interface RedisKeyConstants {

    /**
     * 任务租约 Hash 键前缀，完整键为 {@code agent:task:lease:{taskId}}
     */
    String TASK_LEASE = "agent:task:lease:";

    /**
     * 任务心跳 String 键前缀，完整键为 {@code agent:task:heartbeat:{taskId}}
     */
    String TASK_HEARTBEAT = "agent:task:heartbeat:";

    /**
     * 任务取消信号 String 键前缀，完整键为 {@code agent:task:cancel:{taskId}}
     *
     * <p>值为触发取消的执行代次，Worker 轮询后发现自身代次不高于信号代次即停止
     * 提交、推送与结果写回。取消信号由控制面在任务取消或失租恢复时写入。</p>
     */
    String TASK_CANCEL = "agent:task:cancel:";

    /**
     * 租约 Hash 中持有者字段名
     */
    String LEASE_FIELD_WORKER_ID = "workerId";

    /**
     * 租约 Hash 中执行代次字段名
     */
    String LEASE_FIELD_GENERATION = "generation";

    /**
     * 租约 Hash 中租约到期时间字段名
     */
    String LEASE_FIELD_LEASE_UNTIL = "leaseUntil";

}
