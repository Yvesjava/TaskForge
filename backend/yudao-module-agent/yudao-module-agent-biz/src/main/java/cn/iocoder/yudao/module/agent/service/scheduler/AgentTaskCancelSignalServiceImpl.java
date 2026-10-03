package cn.iocoder.yudao.module.agent.service.scheduler;

import cn.iocoder.yudao.module.agent.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.agent.framework.scheduler.AgentSchedulerProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

/**
 * {@link AgentTaskCancelSignalService} 的 Redis 实现
 *
 * <p>取消信号为 String 键，值为触发取消的执行代次，并带有可配置 TTL。信号不存在或
 * 解析失败时按未取消处理，避免异常值阻塞正常执行。</p>
 *
 * @author TaskForge
 */
@Service
public class AgentTaskCancelSignalServiceImpl implements AgentTaskCancelSignalService {

    private final StringRedisTemplate stringRedisTemplate;

    private final AgentSchedulerProperties schedulerProperties;

    public AgentTaskCancelSignalServiceImpl(StringRedisTemplate stringRedisTemplate,
                                            AgentSchedulerProperties schedulerProperties) {
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate 不能为空");
        this.schedulerProperties = Objects.requireNonNull(schedulerProperties, "schedulerProperties 不能为空");
    }

    @Override
    public void publish(Long taskId, Long generation) {
        requireTaskId(taskId);
        requireGeneration(generation);
        stringRedisTemplate.opsForValue().set(cancelKey(taskId), String.valueOf(generation),
                Duration.ofSeconds(schedulerProperties.getCancelTtlSeconds()));
    }

    @Override
    public boolean isCanceled(Long taskId, Long generation) {
        requireTaskId(taskId);
        requireGeneration(generation);
        String value = stringRedisTemplate.opsForValue().get(cancelKey(taskId));
        if (value == null) {
            return false;
        }
        try {
            return Long.parseLong(value.trim()) >= generation;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    @Override
    public void clear(Long taskId) {
        requireTaskId(taskId);
        stringRedisTemplate.delete(cancelKey(taskId));
    }

    private static String cancelKey(Long taskId) {
        return RedisKeyConstants.TASK_CANCEL + taskId;
    }

    private static void requireTaskId(Long taskId) {
        Objects.requireNonNull(taskId, "taskId 不能为空");
    }

    private static void requireGeneration(Long generation) {
        Objects.requireNonNull(generation, "generation 不能为空");
    }

}
