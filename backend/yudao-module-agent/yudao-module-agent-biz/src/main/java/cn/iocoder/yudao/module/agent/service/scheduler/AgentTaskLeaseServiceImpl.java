package cn.iocoder.yudao.module.agent.service.scheduler;

import cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.agent.framework.scheduler.AgentSchedulerProperties;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.LEASE_FIELD_GENERATION;
import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.LEASE_FIELD_LEASE_UNTIL;
import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.LEASE_FIELD_WORKER_ID;

/**
 * {@link AgentTaskLeaseService} 的 Redis 实现
 *
 * <p>租约键为 Hash，记录持有者、执行代次与到期时间；心跳键为 String，记录
 * {@code workerId:generation:timestamp}。续租在 Redis 侧执行读-改-写持有者校验，
 * 权威的原子条件更新仍由 MySQL 的 {@code renewLease} 完成。</p>
 *
 * @author TaskForge
 */
@Service
public class AgentTaskLeaseServiceImpl implements AgentTaskLeaseService {

    private static final DateTimeFormatter LEASE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final StringRedisTemplate stringRedisTemplate;

    private final AgentSchedulerProperties schedulerProperties;

    private final AgentTaskCancelSignalService cancelSignalService;

    public AgentTaskLeaseServiceImpl(StringRedisTemplate stringRedisTemplate,
                                     AgentSchedulerProperties schedulerProperties,
                                     AgentTaskCancelSignalService cancelSignalService) {
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate 不能为空");
        this.schedulerProperties = Objects.requireNonNull(schedulerProperties, "schedulerProperties 不能为空");
        this.cancelSignalService = Objects.requireNonNull(cancelSignalService, "cancelSignalService 不能为空");
    }

    @Override
    public void acquire(Long taskId, String workerId, Long generation, LocalDateTime leaseUntil) {
        requireTaskId(taskId);
        requireHolder(workerId, generation);
        Objects.requireNonNull(leaseUntil, "leaseUntil 不能为空");

        // 新一轮执行开始，清除上一代次残留的取消信号，避免旧信号影响新 Worker
        cancelSignalService.clear(taskId);

        Map<String, String> fields = new HashMap<>();
        fields.put(LEASE_FIELD_WORKER_ID, workerId);
        fields.put(LEASE_FIELD_GENERATION, String.valueOf(generation));
        fields.put(LEASE_FIELD_LEASE_UNTIL, LEASE_TIME_FORMATTER.format(leaseUntil));
        hashOperations().putAll(leaseKey(taskId), fields);
        stringRedisTemplate.expire(leaseKey(taskId), Duration.ofSeconds(leaseTtlSeconds(leaseUntil)));
        heartbeat(taskId, workerId, generation);
    }

    @Override
    public void heartbeat(Long taskId, String workerId, Long generation) {
        requireTaskId(taskId);
        requireHolder(workerId, generation);
        String value = workerId + ":" + generation + ":" + System.currentTimeMillis();
        stringRedisTemplate.opsForValue().set(heartbeatKey(taskId), value,
                Duration.ofSeconds(schedulerProperties.getHeartbeatTtlSeconds()));
    }

    @Override
    public boolean renew(Long taskId, String workerId, Long generation, LocalDateTime leaseUntil) {
        requireTaskId(taskId);
        requireHolder(workerId, generation);
        Objects.requireNonNull(leaseUntil, "leaseUntil 不能为空");

        if (!isCurrentHolder(taskId, workerId, generation)) {
            return false;
        }
        hashOperations().put(leaseKey(taskId), LEASE_FIELD_LEASE_UNTIL,
                LEASE_TIME_FORMATTER.format(leaseUntil));
        stringRedisTemplate.expire(leaseKey(taskId), Duration.ofSeconds(leaseTtlSeconds(leaseUntil)));
        heartbeat(taskId, workerId, generation);
        return true;
    }

    @Override
    public boolean isCurrentHolder(Long taskId, String workerId, Long generation) {
        requireTaskId(taskId);
        requireHolder(workerId, generation);
        Map<Object, Object> lease = hashOperations().entries(leaseKey(taskId));
        if (lease.isEmpty()) {
            return false;
        }
        return workerId.equals(lease.get(LEASE_FIELD_WORKER_ID))
                && String.valueOf(generation).equals(lease.get(LEASE_FIELD_GENERATION));
    }

    @Override
    public void release(Long taskId) {
        requireTaskId(taskId);
        stringRedisTemplate.delete(leaseKey(taskId));
        stringRedisTemplate.delete(heartbeatKey(taskId));
        cancelSignalService.clear(taskId);
    }

    private HashOperations<String, Object, Object> hashOperations() {
        return stringRedisTemplate.opsForHash();
    }

    private long leaseTtlSeconds(LocalDateTime leaseUntil) {
        long seconds = Duration.between(LocalDateTime.now(), leaseUntil).getSeconds()
                + schedulerProperties.getLeaseBufferSeconds();
        return Math.max(seconds, 1L);
    }

    private static String leaseKey(Long taskId) {
        return RedisKeyConstants.TASK_LEASE + taskId;
    }

    private static String heartbeatKey(Long taskId) {
        return RedisKeyConstants.TASK_HEARTBEAT + taskId;
    }

    private static void requireTaskId(Long taskId) {
        Objects.requireNonNull(taskId, "taskId 不能为空");
    }

    private static void requireHolder(String workerId, Long generation) {
        Objects.requireNonNull(workerId, "workerId 不能为空");
        Objects.requireNonNull(generation, "generation 不能为空");
    }

}
