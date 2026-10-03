package cn.iocoder.yudao.module.agent.framework.webhook;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * 基于 Redis 的 Webhook nonce 存储
 *
 * <p>使用 {@code SET key "" NX PX ttl} 的原子语义占用 nonce，避免并发下重复
 * 回调同时通过回放校验。键带独立前缀，不与其他业务键冲突。</p>
 *
 * @author TaskForge
 */
@Component
public class RedisWebhookNonceStore implements WebhookNonceStore {

    private static final String KEY_PREFIX = "agent:webhook:nonce:";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisWebhookNonceStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate 不能为空");
    }

    @Override
    public boolean claim(String nonce, Duration ttl) {
        Objects.requireNonNull(nonce, "nonce 不能为空");
        if (nonce.isBlank()) {
            throw new IllegalArgumentException("nonce 不能为空");
        }
        Duration effectiveTtl = ttl == null ? Duration.ofMinutes(10) : ttl;
        Boolean claimed = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + nonce, "", effectiveTtl);
        return Boolean.TRUE.equals(claimed);
    }

}
