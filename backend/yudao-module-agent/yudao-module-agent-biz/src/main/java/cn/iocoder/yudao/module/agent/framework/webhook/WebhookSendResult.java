package cn.iocoder.yudao.module.agent.framework.webhook;

import java.util.List;

/**
 * Webhook 投递重试归档结果
 *
 * <p>归档首次发送与每次重试的独立结果，供上层决定是否进入补偿/告警。</p>
 *
 * @param attempts  按发送顺序排列的全部尝试
 * @param maxRetries 允许的最大重试次数（不含首次发送）
 * @author TaskForge
 */
public record WebhookSendResult(List<WebhookAttempt> attempts, int maxRetries) {

    public WebhookSendResult {
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries 不能为负数");
        }
    }

    public int retryCount() {
        return attempts.isEmpty() ? 0 : attempts.size() - 1;
    }

    public WebhookAttempt firstAttempt() {
        return attempts.isEmpty() ? null : attempts.get(0);
    }

    public WebhookAttempt lastAttempt() {
        return attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
    }

    public boolean isSuccess() {
        WebhookAttempt last = lastAttempt();
        return last != null && last.isSuccess();
    }

    public boolean isRetryBudgetExhausted() {
        return retryCount() >= maxRetries;
    }

    public boolean failedAfterRetries() {
        return isRetryBudgetExhausted() && !isSuccess();
    }

    public long totalDurationMillis() {
        return attempts.stream().mapToLong(WebhookAttempt::durationMillis).sum();
    }

}
