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

import static cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants.TASK_CANCEL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-SCHED-03 取消信号与 Worker 停止写入规则测试
 *
 * <p>使用内嵌 Redis（jedis-mock）验证取消信号写入、代次比较、清理，以及
 * {@link WorkerWriteGate} 在取消或失租后禁止提交、推送与结果写回。</p>
 *
 * @author TaskForge
 */
class AgentTaskCancelSignalTest {

    private static RedisServer redisServer;

    private static RedissonClient redissonClient;

    private static StringRedisTemplate stringRedisTemplate;

    private static AgentTaskCancelSignalServiceImpl cancelSignalService;

    private static AgentTaskLeaseServiceImpl leaseService;

    private static WorkerWriteGate writeGate;

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
        properties.setCancelTtlSeconds(300);

        cancelSignalService = new AgentTaskCancelSignalServiceImpl(stringRedisTemplate, properties);
        leaseService = new AgentTaskLeaseServiceImpl(stringRedisTemplate, properties, cancelSignalService);
        writeGate = new WorkerWriteGate(cancelSignalService, leaseService);
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
    void publish_writesGenerationAndTtl() {
        cancelSignalService.publish(1001L, 3L);

        assertThat(stringRedisTemplate.opsForValue().get(TASK_CANCEL + 1001L)).isEqualTo("3");
        assertThat(stringRedisTemplate.getExpire(TASK_CANCEL + 1001L)).isPositive();
    }

    @Test
    void isCanceled_returnsFalse_whenNoSignal() {
        assertThat(cancelSignalService.isCanceled(2001L, 3L)).isFalse();
    }

    @Test
    void isCanceled_stopsSameAndOlderGeneration_butNotNewerGeneration() {
        cancelSignalService.publish(3001L, 5L);

        assertThat(cancelSignalService.isCanceled(3001L, 5L)).isTrue();
        assertThat(cancelSignalService.isCanceled(3001L, 4L)).isTrue();
        assertThat(cancelSignalService.isCanceled(3001L, 6L)).isFalse();
    }

    @Test
    void isCanceled_ignoresMalformedSignalValue() {
        stringRedisTemplate.opsForValue().set(TASK_CANCEL + 4001L, "not-a-number");

        assertThat(cancelSignalService.isCanceled(4001L, 1L)).isFalse();
    }

    @Test
    void clear_removesSignal() {
        cancelSignalService.publish(5001L, 2L);
        assertThat(cancelSignalService.isCanceled(5001L, 2L)).isTrue();

        cancelSignalService.clear(5001L);

        assertThat(cancelSignalService.isCanceled(5001L, 2L)).isFalse();
        assertThat(stringRedisTemplate.hasKey(TASK_CANCEL + 5001L)).isFalse();
    }

    @Test
    void gate_allowsCommitPushAndWriteBack_whenHoldingLeaseWithoutCancel() {
        leaseService.acquire(6001L, "worker-a", 3L, LocalDateTime.now().plusMinutes(5));

        WorkerWriteDecision decision = writeGate.check(6001L, "worker-a", 3L);

        assertThat(decision.commitAllowed()).isTrue();
        assertThat(decision.pushAllowed()).isTrue();
        assertThat(decision.writeBackAllowed()).isTrue();
        assertThat(decision.isWriteAllowed()).isTrue();
    }

    @Test
    void gate_blocksAllWrites_afterCancelSignal() {
        leaseService.acquire(7001L, "worker-b", 5L, LocalDateTime.now().plusMinutes(5));

        cancelSignalService.publish(7001L, 5L);
        WorkerWriteDecision decision = writeGate.check(7001L, "worker-b", 5L);

        assertThat(decision.commitAllowed()).isFalse();
        assertThat(decision.pushAllowed()).isFalse();
        assertThat(decision.writeBackAllowed()).isFalse();
    }

    @Test
    void gate_blocksAllWrites_afterLeaseLoss() {
        leaseService.acquire(8001L, "worker-c", 7L, LocalDateTime.now().plusMinutes(5));

        leaseService.release(8001L);
        WorkerWriteDecision decision = writeGate.check(8001L, "worker-c", 7L);

        assertThat(decision.commitAllowed()).isFalse();
        assertThat(decision.pushAllowed()).isFalse();
        assertThat(decision.writeBackAllowed()).isFalse();
    }

    @Test
    void gate_blocksAllWrites_whenGenerationMismatch() {
        leaseService.acquire(9001L, "worker-d", 11L, LocalDateTime.now().plusMinutes(5));

        WorkerWriteDecision decision = writeGate.check(9001L, "worker-d", 12L);

        assertThat(decision.commitAllowed()).isFalse();
        assertThat(decision.pushAllowed()).isFalse();
        assertThat(decision.writeBackAllowed()).isFalse();
    }

    @Test
    void gate_allowsNewGeneration_afterOlderCancelSignal() {
        cancelSignalService.publish(10001L, 3L);

        leaseService.acquire(10001L, "worker-e", 5L, LocalDateTime.now().plusMinutes(5));
        WorkerWriteDecision decision = writeGate.check(10001L, "worker-e", 5L);

        assertThat(decision.commitAllowed()).isTrue();
        assertThat(decision.pushAllowed()).isTrue();
        assertThat(decision.writeBackAllowed()).isTrue();
    }

}
