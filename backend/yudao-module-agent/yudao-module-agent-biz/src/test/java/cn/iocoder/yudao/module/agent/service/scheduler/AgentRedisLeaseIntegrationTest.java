package cn.iocoder.yudao.module.agent.service.scheduler;

import cn.iocoder.yudao.module.agent.framework.scheduler.AgentSchedulerProperties;
import cn.iocoder.yudao.module.agent.framework.webhook.RedisWebhookNonceStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.LEASE_FIELD_GENERATION;
import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.LEASE_FIELD_LEASE_UNTIL;
import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.LEASE_FIELD_WORKER_ID;
import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.TASK_CANCEL;
import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.TASK_HEARTBEAT;
import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.TASK_LEASE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-QA-02 Redis 集成测试：在真实 Redis 7 容器中验证租约、心跳、取消信号、
 * 执行代次守卫与 Worker 停止写入门禁。
 *
 * <p>与 {@code AgentTaskLeaseTest} / {@code AgentTaskCancelSignalTest} 的内嵌
 * jedis-mock 单元测试互补，本测试验证 Redisson 客户端与真实 Redis 服务器的兼容性，
 * 以及 TTL、Hash 写入、条件续租等行为。</p>
 */
@Testcontainers
class AgentRedisLeaseIntegrationTest {

    private static final DateTimeFormatter LEASE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    private static RedissonClient redissonClient;

    private static StringRedisTemplate stringRedisTemplate;

    private static AgentTaskLeaseServiceImpl leaseService;

    private static AgentTaskCancelSignalServiceImpl cancelSignalService;

    private static WorkerWriteGate writeGate;

    @BeforeAll
    static void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redissonClient = Redisson.create(config);

        RedissonConnectionFactory connectionFactory = new RedissonConnectionFactory(redissonClient);
        stringRedisTemplate = new StringRedisTemplate(connectionFactory);
        stringRedisTemplate.afterPropertiesSet();

        AgentSchedulerProperties properties = new AgentSchedulerProperties();
        properties.setLeaseMinutes(5);
        properties.setLeaseBufferSeconds(60);
        properties.setHeartbeatTtlSeconds(90);
        properties.setCancelTtlSeconds(300);

        cancelSignalService = new AgentTaskCancelSignalServiceImpl(stringRedisTemplate, properties);
        leaseService = new AgentTaskLeaseServiceImpl(stringRedisTemplate, properties, cancelSignalService);
        writeGate = new WorkerWriteGate(cancelSignalService, leaseService);
    }

    @AfterAll
    static void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void acquire_writesLeaseHashAndHeartbeatWithTtl() {
        LocalDateTime leaseUntil = LocalDateTime.now().plusMinutes(5);

        leaseService.acquire(1001L, "worker-a", 3L, leaseUntil);

        Map<Object, Object> lease = stringRedisTemplate.opsForHash().entries(TASK_LEASE + 1001L);
        assertThat(lease.get(LEASE_FIELD_WORKER_ID)).isEqualTo("worker-a");
        assertThat(lease.get(LEASE_FIELD_GENERATION)).isEqualTo("3");
        assertThat(lease.get(LEASE_FIELD_LEASE_UNTIL)).isEqualTo(LEASE_TIME_FORMATTER.format(leaseUntil));

        assertThat(stringRedisTemplate.getExpire(TASK_LEASE + 1001L)).isPositive();
        assertThat(stringRedisTemplate.getExpire(TASK_HEARTBEAT + 1001L)).isPositive();

        String heartbeat = stringRedisTemplate.opsForValue().get(TASK_HEARTBEAT + 1001L);
        assertThat(heartbeat).startsWith("worker-a:3:");
    }

    @Test
    void renew_updatesLeaseOnlyForCurrentHolderAndGeneration() {
        LocalDateTime first = LocalDateTime.now().plusMinutes(5);
        LocalDateTime second = LocalDateTime.now().plusMinutes(10);
        leaseService.acquire(2001L, "worker-b", 7L, first);

        assertThat(leaseService.renew(2001L, "worker-b", 7L, second)).isTrue();
        assertThat(leaseService.renew(2001L, "worker-other", 7L, second)).isFalse();
        assertThat(leaseService.renew(2001L, "worker-b", 8L, second)).isFalse();

        Map<Object, Object> lease = stringRedisTemplate.opsForHash().entries(TASK_LEASE + 2001L);
        assertThat(lease.get(LEASE_FIELD_WORKER_ID)).isEqualTo("worker-b");
        assertThat(lease.get(LEASE_FIELD_GENERATION)).isEqualTo("7");
        assertThat(lease.get(LEASE_FIELD_LEASE_UNTIL)).isEqualTo(LEASE_TIME_FORMATTER.format(second));
    }

    @Test
    void release_removesLeaseHeartbeatAndCancelKeys() {
        leaseService.acquire(3001L, "worker-c", 2L, LocalDateTime.now().plusMinutes(5));
        cancelSignalService.publish(3001L, 2L);

        leaseService.release(3001L);

        assertThat(stringRedisTemplate.hasKey(TASK_LEASE + 3001L)).isFalse();
        assertThat(stringRedisTemplate.hasKey(TASK_HEARTBEAT + 3001L)).isFalse();
        assertThat(stringRedisTemplate.hasKey(TASK_CANCEL + 3001L)).isFalse();
    }

    @Test
    void cancelSignal_stopsSameAndOlderGenerationButNotNewer() {
        cancelSignalService.publish(4001L, 5L);

        assertThat(cancelSignalService.isCanceled(4001L, 5L)).isTrue();
        assertThat(cancelSignalService.isCanceled(4001L, 4L)).isTrue();
        assertThat(cancelSignalService.isCanceled(4001L, 6L)).isFalse();
        assertThat(stringRedisTemplate.getExpire(TASK_CANCEL + 4001L)).isPositive();
    }

    @Test
    void cancelSignal_clearRemovesSignal() {
        cancelSignalService.publish(5001L, 2L);
        assertThat(cancelSignalService.isCanceled(5001L, 2L)).isTrue();

        cancelSignalService.clear(5001L);

        assertThat(cancelSignalService.isCanceled(5001L, 2L)).isFalse();
        assertThat(stringRedisTemplate.hasKey(TASK_CANCEL + 5001L)).isFalse();
    }

    @Test
    void writeGate_allowsCurrentHolderAndBlocksCanceledLostLeaseOrMismatchedGeneration() {
        leaseService.acquire(6001L, "worker-d", 9L, LocalDateTime.now().plusMinutes(5));
        assertThat(writeGate.check(6001L, "worker-d", 9L).isWriteAllowed()).isTrue();

        cancelSignalService.publish(6001L, 9L);
        assertThat(writeGate.check(6001L, "worker-d", 9L).isWriteAllowed()).isFalse();

        cancelSignalService.clear(6001L);
        leaseService.release(6001L);
        assertThat(writeGate.check(6001L, "worker-d", 9L).isWriteAllowed()).isFalse();

        leaseService.acquire(6001L, "worker-d", 10L, LocalDateTime.now().plusMinutes(5));
        assertThat(writeGate.check(6001L, "worker-d", 9L).isWriteAllowed()).isFalse();
        assertThat(writeGate.check(6001L, "worker-d", 10L).isWriteAllowed()).isTrue();
    }

    @Test
    void nonceClaim_occupiesValueOnlyOnceOnRealRedis() {
        RedisWebhookNonceStore nonceStore = new RedisWebhookNonceStore(stringRedisTemplate);

        assertThat(nonceStore.claim("nonce-1", Duration.ofMinutes(10))).isTrue();
        assertThat(nonceStore.claim("nonce-1", Duration.ofMinutes(10))).isFalse();
        assertThat(nonceStore.claim("nonce-2", Duration.ofMinutes(10))).isTrue();
    }

}
