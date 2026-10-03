package cn.iocoder.yudao.module.agent.framework.webhook;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单次 Webhook 投递请求
 *
 * <p>承载目标地址、已渲染好的消息 payload 与本次投递的超时时间。
 * 目标地址可能携带机器人密钥，因此禁止直接写入日志。</p>
 *
 * @param url     群机器人 Webhook 地址
 * @param payload 平台消息结构（已由适配器渲染）
 * @param timeout 单次投递超时；为空时使用 {@link WebhookProperties#getReadTimeout()}
 * @author TaskForge
 */
public record WebhookRequest(String url, Map<String, Object> payload, Duration timeout) {

    public WebhookRequest {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("webhook url 不能为空");
        }
        payload = payload == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public static WebhookRequest of(String url, Map<String, Object> payload, Duration timeout) {
        return new WebhookRequest(url, payload, timeout);
    }

}
