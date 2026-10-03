package cn.iocoder.yudao.module.agent.service.scheduler;

import java.time.LocalDateTime;

/**
 * 任务 Redis 租约与心跳服务
 *
 * <p>MySQL 是任务状态与租约的最终事实来源，本服务负责维护 Redis 中的实时租约与
 * 心跳键，用于快速发现租约异常和判断 Worker 存活。续租必须同时校验 Worker 身份与
 * 执行代次，只有当前 Worker 与当前代次才能更新，防止旧 Worker 覆盖新结果。</p>
 *
 * @author TaskForge
 */
public interface AgentTaskLeaseService {

    /**
     * 抢占成功后写入租约与心跳键。
     *
     * @param taskId     任务主键 ID
     * @param workerId   当前 Worker 身份
     * @param generation 本次执行代次
     * @param leaseUntil 租约到期时间
     */
    void acquire(Long taskId, String workerId, Long generation, LocalDateTime leaseUntil);

    /**
     * 发送一次心跳。
     *
     * <p>心跳值格式为 {@code workerId:generation:timestamp}，并刷新 90 秒 TTL。</p>
     *
     * @param taskId     任务主键 ID
     * @param workerId   当前 Worker 身份
     * @param generation 本次执行代次
     */
    void heartbeat(Long taskId, String workerId, Long generation);

    /**
     * 续租，仅允许当前 Worker 与当前代次更新。
     *
     * <p>当 Redis 租约不存在、持有者不一致或执行代次不一致时返回 {@code false}，
     * 且不修改任何键；续租成功时刷新租约到期时间、租约 TTL 与心跳。</p>
     *
     * @param taskId     任务主键 ID
     * @param workerId   当前 Worker 身份
     * @param generation 本次执行代次
     * @param leaseUntil 新的租约到期时间
     * @return {@code true} 表示续租成功，{@code false} 表示已失租或代次不匹配
     */
    boolean renew(Long taskId, String workerId, Long generation, LocalDateTime leaseUntil);

    /**
     * 判断指定 Worker 与代次是否为当前租约持有者。
     *
     * @param taskId     任务主键 ID
     * @param workerId   待校验 Worker 身份
     * @param generation 待校验执行代次
     * @return {@code true} 表示当前租约由该 Worker 与该代次持有
     */
    boolean isCurrentHolder(Long taskId, String workerId, Long generation);

    /**
     * 释放任务租约与心跳键（任务完成、失败或重置时调用）。
     *
     * @param taskId 任务主键 ID
     */
    void release(Long taskId);

}
