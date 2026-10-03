package cn.iocoder.yudao.module.agent.framework.webhook;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.agent.framework.notice.NoticeSecretRedactor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * 基于 JDK HttpClient 的 Webhook 投递客户端
 *
 * <p>连接超时与读取超时分别来自 {@link WebhookProperties}，读取超时也可由
 * {@link WebhookRequest#timeout()} 覆盖。日志中的目标地址会被脱敏，避免泄露
 * 群机器人 Webhook 密钥。</p>
 *
 * @author TaskForge
 */
@Component
@Slf4j
public class JdkWebhookClient implements WebhookHttpClient {

    private final HttpClient httpClient;
    private final WebhookProperties properties;
    private final WebhookSignature signature;

    public JdkWebhookClient(WebhookProperties properties, WebhookSignature signature) {
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
        this.signature = Objects.requireNonNull(signature, "signature 不能为空");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(resolveConnectTimeout(properties.getConnectTimeout()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public WebhookResponse post(WebhookRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        Duration timeout = request.timeout() == null
                ? resolveReadTimeout(properties.getReadTimeout()) : request.timeout();
        String body = JsonUtils.toJsonString(request.payload());
        try {
            WebhookSignedRequest signed = signature.sign(body);
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(request.url()))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header(WebhookSignatureHeaders.TIMESTAMP, signed.timestamp())
                    .header(WebhookSignatureHeaders.NONCE, signed.nonce())
                    .header(WebhookSignatureHeaders.SIGNATURE, signed.signature())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("[WebhookClient] 投递完成 status={} target={}",
                    response.statusCode(), redactedTarget(request.url()));
            return WebhookResponse.success(response.statusCode(), response.body());
        } catch (HttpTimeoutException e) {
            log.warn("[WebhookClient] 投递超时 target={} timeout={}",
                    redactedTarget(request.url()), timeout);
            return WebhookResponse.timeout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WebhookResponse.failure(0, "", "投递被中断");
        } catch (Exception e) {
            log.warn("[WebhookClient] 投递失败 target={} reason={}",
                    redactedTarget(request.url()), reasonOf(e));
            return WebhookResponse.failure(0, "", reasonOf(e));
        }
    }

    private Duration resolveConnectTimeout(Duration timeout) {
        return timeout == null ? Duration.ofSeconds(5) : timeout;
    }

    private Duration resolveReadTimeout(Duration timeout) {
        return timeout == null ? Duration.ofSeconds(10) : timeout;
    }

    private String redactedTarget(String url) {
        String sanitized = NoticeSecretRedactor.redact(url);
        try {
            URI uri = URI.create(sanitized);
            String host = uri.getHost() == null ? "unknown" : uri.getHost();
            return (uri.getScheme() == null ? "" : uri.getScheme() + "://") + host + "/***";
        } catch (Exception e) {
            return "***";
        }
    }

    private String reasonOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

}
