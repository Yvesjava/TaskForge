package cn.iocoder.yudao.module.agent.framework.webhook;

import java.time.Duration;

/**
 * Webhook 回调 nonce 一次性存储
 *
 * <p>用于回放防护：同一 nonce 只能被成功占用一次，TTL 到期后自动释放。
 * 校验签名通过后才能调用 {@link #claim(String, Duration)}，避免攻击者用非法请求
 * 提前消耗合法 nonce。</p>
 *
 * @author TaskForge
 */
public interface WebhookNonceStore {

    /**
     * 原子占用一个 nonce。
     *
     * @param nonce nonce 值，不能为空
     * @param ttl   占用有效期
     * @return {@code true} 表示首次占用成功；{@code false} 表示 nonce 已被占用（重放）
     */
    boolean claim(String nonce, Duration ttl);

}
