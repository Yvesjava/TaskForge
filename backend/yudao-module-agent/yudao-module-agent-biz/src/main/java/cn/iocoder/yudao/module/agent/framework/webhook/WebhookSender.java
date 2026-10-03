package cn.iocoder.yudao.module.agent.framework.webhook;

import cn.iocoder.yudao.module.agent.framework.observability.AgentObservability;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Webhook 发送与重试编排器
 *
 * <p>在 {@link WebhookHttpClient} 之上实现“发送、超时与错误可重试”：首次发送
 * 失败后，仅对超时、连接错误与服务端 5xx 这类瞬时失败进行重试，4xx 视为请求
 * 本身不被接收而不重试。重试预算与退避由 {@link WebhookProperties} 控制。</p>
 *
 * @author TaskForge
 */
@Component
@Slf4j
public class WebhookSender {

    private final WebhookHttpClient client;
    private final int maxRetries;
    private final long retryBackoffMillis;
    private final AgentObservability observability;

    public WebhookSender(WebhookHttpClient client, WebhookProperties properties,
                         AgentObservability observability) {
        this.client = Objects.requireNonNull(client, "client 不能为空");
        this.maxRetries = Math.max(0, properties.getMaxRetries());
        this.retryBackoffMillis = Math.max(0, properties.getRetryBackoffMillis());
        this.observability = Objects.requireNonNull(observability, "observability 不能为空");
    }

    /**
     * 发送一次 Webhook，必要时按预算重试并归档每次尝试。
     *
     * @param request 投递请求
     * @return 投递归档结果
     */
    public WebhookSendResult send(WebhookRequest request) {
        Objects.requireNonNull(request, "request 不能为空");

        AgentObservability.ExternalCallObservation observation =
                observability.startExternalCall("webhook", hostOf(request.url()));
        try {
            List<WebhookAttempt> attempts = new ArrayList<>();
            WebhookAttempt current = execute(request, 1);
            attempts.add(current);

            while (current.response().isRetryable() && attempts.size() - 1 < maxRetries) {
                sleepBackoff(attempts.size());
                int attemptNumber = attempts.size() + 1;
                log.info("[WebhookSender] 重试投递 attempt={}/{} previousReason={} traceId={}",
                        attemptNumber, maxRetries + 1, current.failureReason(), observability.traceId());
                current = execute(request, attemptNumber);
                attempts.add(current);
            }

            WebhookSendResult result = new WebhookSendResult(attempts, maxRetries);
            if (result.isSuccess()) {
                observation.success();
            } else {
                WebhookAttempt last = result.lastAttempt();
                if (last != null && last.timedOut()) {
                    observation.timeout();
                } else {
                    observation.failure();
                }
            }
            return result;
        } finally {
            observation.close();
        }
    }

    private WebhookAttempt execute(WebhookRequest request, int attemptNumber) {
        long started = System.currentTimeMillis();
        WebhookResponse response;
        try {
            response = client.post(request);
        } catch (Exception e) {
            response = WebhookResponse.failure(0, "", reasonOf(e));
        }
        return new WebhookAttempt(attemptNumber, response, System.currentTimeMillis() - started);
    }

    private void sleepBackoff(int retryNumber) {
        if (retryBackoffMillis <= 0) {
            return;
        }
        long wait = Math.multiplyExact(retryBackoffMillis, retryNumber);
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String reasonOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "unknown" : host;
        } catch (Exception e) {
            return "unknown";
        }
    }

}
