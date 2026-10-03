package cn.iocoder.yudao.module.agent.framework.webhook;

import java.util.Objects;

/**
 * 单次 Webhook 投递尝试
 *
 * <p>重试归档的基本单元，独立记录尝试序号、响应与耗时，便于上层排查与统计。</p>
 *
 * @param attemptNumber 尝试序号，从 1 开始
 * @param response      单次投递响应（始终非空）
 * @param durationMillis 本次尝试耗时（毫秒）
 * @author TaskForge
 */
public record WebhookAttempt(int attemptNumber, WebhookResponse response, long durationMillis) {

    public WebhookAttempt {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber 必须从 1 开始");
        }
        Objects.requireNonNull(response, "response 不能为空");
    }

    public boolean isSuccess() {
        return response.isSuccess();
    }

    public boolean timedOut() {
        return response.timedOut();
    }

    public String failureReason() {
        return isSuccess() ? "" : response.failureReason();
    }

}
