package cn.iocoder.yudao.module.agent.service.scheduler;

import com.github.fppt.jedismock.RedisServer;
import cn.iocoder.yudao.module.agent.framework.scheduler.AgentSchedulerProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.LEASE_FIELD_GENERATION;
import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.LEASE_FIELD_LEASE_UNTIL;
import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.LEASE_FIELD_WORKER_ID;
import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.TASK_HEARTBEAT;
import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.TASK_LEASE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentTaskLeaseServiceImpl} 的 Redis 租约与心跳测试
 *
 * <p>使用内嵌 Redis（jedis-mock）验证租约键写入、心跳、持有者校验、续租代次校验与
 * 释放行为。核心验收：续租只允许当前 Worker 与当前执行代次更新。</p>
 *
 * @author TaskForge
 */
class AgentTaskLeaseTest {

    private static RedisServer redisServer;

    private static RedissonClient redissonClient;

    private static StringRedisTemplate stringRedisTemplate;

    private static AgentTaskLeaseServiceImpl leaseService;

    @BeforeAll
    static void setUp() throws Exception {
        redisServer = RedisServer.newRedisServer();
        redisServer.start();

        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:" + redisServer.getBindPort());
        redissonClient = Redisson.create(config);

        RedissonConnectionFactory connectionFactory = new RedissonConnectionFactory(redissonClient);
        stringRedisTemplate = new StringRedisTemplate(connectionFactory);
        stringRedisTemplate.afterPropertiesSet();

        AgentSchedulerProperties properties = new AgentSchedulerProperties();
        properties.setLeaseMinutes(5);
        properties.setLeaseBufferSeconds(60);
        properties.setHeartbeatTtlSeconds(90);
        leaseService = new AgentTaskLeaseServiceImpl(stringRedisTemplate, properties);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @Test
    void acquire_writesLeaseAndHeartbeatKeys() {
        LocalDateTime leaseUntil = LocalDateTime.now().plusMinutes(5);

        leaseService.acquire(1001L, "worker-a", 3L, leaseUntil);

        Map<Object, Object> lease = hashEntries(1001L);
        assertThat(lease.get(LEASE_FIELD_WORKER_ID)).isEqualTo("worker-a");
        assertThat(lease.get(LEASE_FIELD_GENERATION)).isEqualTo("3");
        assertThat(lease.get(LEASE_FIELD_LEASE_UNTIL)).isEqualTo(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(leaseUntil));

        assertThat(stringRedisTemplate.getExpire(TASK_LEASE + 1001L)).isPositive();
        assertThat(stringRedisTemplate.getExpire(TASK_HEARTBEAT + 1001L)).isPositive();

        String heartbeat = stringRedisTemplate.opsForValue().get(TASK_HEARTBEAT + 1001L);
        assertThat(heartbeat).startsWith("worker-a:3:");
    }

    @Test
    void renew_succeeds_whenCurrentWorkerAndGeneration() {
        LocalDateTime first = LocalDateTime.now().plusMinutes(5);
        LocalDateTime second = LocalDateTime.now().plusMinutes(10);
        leaseService.acquire(2001L, "worker-b", 7L, first);

        boolean renewed = leaseService.renew(2001L, "worker-b", 7L, second);

        assertThat(renewed).isTrue();
        Map<Object, Object> lease = hashEntries(2001L);
        assertThat(lease.get(LEASE_FIELD_WORKER_ID)).isEqualTo("worker-b");
        assertThat(lease.get(LEASE_FIELD_GENERATION)).isEqualTo("7");
        assertThat(lease.get(LEASE_FIELD_LEASE_UNTIL)).isEqualTo(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(second));
    }

    @Test
    void renew_rejected_whenWrongWorker() {
        LocalDateTime leaseUntil = LocalDateTime.now().plusMinutes(5);
        leaseService.acquire(3001L, "worker-c", 5L, leaseUntil);

        boolean renewed = leaseService.renew(3001L, "worker-other", 5L, LocalDateTime.now().plusMinutes(10));

        assertThat(renewed).isFalse();
        Map<Object, Object> lease = hashEntries(3001L);
        assertThat(lease.get(LEASE_FIELD_WORKER_ID)).isEqualTo("worker-c");
        assertThat(lease.get(LEASE_FIELD_LEASE_UNTIL)).isEqualTo(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(leaseUntil));
    }

    @Test
    void renew_rejected_whenWrongGeneration() {
        LocalDateTime leaseUntil = LocalDateTime.now().plusMinutes(5);
        leaseService.acquire(4001L, "worker-d", 11L, leaseUntil);

        boolean renewed = leaseService.renew(4001L, "worker-d", 12L, LocalDateTime.now().plusMinutes(10));

        assertThat(renewed).isFalse();
        Map<Object, Object> lease = hashEntries(4001L);
        assertThat(lease.get(LEASE_FIELD_GENERATION)).isEqualTo("11");
        assertThat(lease.get(LEASE_FIELD_LEASE_UNTIL)).isEqualTo(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(leaseUntil));
    }

    @Test
    void renew_rejected_whenLeaseAbsent() {
        boolean renewed = leaseService.renew(5001L, "worker-e", 1L, LocalDateTime.now().plusMinutes(10));

        assertThat(renewed).isFalse();
        assertThat(stringRedisTemplate.hasKey(TASK_LEASE + 5001L)).isFalse();
    }

    @Test
    void isCurrentHolder_checksWorkerAndGeneration() {
        leaseService.acquire(6001L, "worker-f", 9L, LocalDateTime.now().plusMinutes(5));

        assertThat(leaseService.isCurrentHolder(6001L, "worker-f", 9L)).isTrue();
        assertThat(leaseService.isCurrentHolder(6001L, "worker-other", 9L)).isFalse();
        assertThat(leaseService.isCurrentHolder(6001L, "worker-f", 10L)).isFalse();
        assertThat(leaseService.isCurrentHolder(7001L, "worker-f", 9L)).isFalse();
    }

    @Test
    void release_removesLeaseAndHeartbeatKeys() {
        leaseService.acquire(8001L, "worker-g", 2L, LocalDateTime.now().plusMinutes(5));
        assertThat(stringRedisTemplate.hasKey(TASK_LEASE + 8001L)).isTrue();
        assertThat(stringRedisTemplate.hasKey(TASK_HEARTBEAT + 8001L)).isTrue();

        leaseService.release(8001L);

        assertThat(stringRedisTemplate.hasKey(TASK_LEASE + 8001L)).isFalse();
        assertThat(stringRedisTemplate.hasKey(TASK_HEARTBEAT + 8001L)).isFalse();
    }

    private static Map<Object, Object> hashEntries(long taskId) {
        return stringRedisTemplate.opsForHash().entries(TASK_LEASE + taskId);
    }

}
