package cn.iocoder.yudao.module.agent.framework.webhook;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Webhook HMAC-SHA256 签名与回放防护
 *
 * <p>出站卡片由 {@link #sign(String)} 生成时间戳、nonce 与签名；入站回调由
 * {@link #verify(String, String, String, String)} 校验时间窗、签名与 nonce 唯一性。
 * 签名字符串为 {@code timestamp + "\n" + nonce + "\n" + rawBody}。</p>
 *
 * @author TaskForge
 */
@Component
public class WebhookSignature {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String NEWLINE = "\n";

    private final String secret;
    private final Duration timeWindow;
    private final WebhookNonceStore nonceStore;

    public WebhookSignature(WebhookProperties properties, WebhookNonceStore nonceStore) {
        Objects.requireNonNull(properties, "properties 不能为空");
        this.secret = properties.getSecret() == null ? "" : properties.getSecret();
        this.timeWindow = properties.getTimeWindow() == null
                ? Duration.ofMinutes(5) : properties.getTimeWindow();
        this.nonceStore = Objects.requireNonNull(nonceStore, "nonceStore 不能为空");
    }

    /**
     * 为出站 Webhook 正文生成签名头。
     *
     * @param rawBody 即将发送的原始 JSON 正文；为空时按空串处理
     * @return 时间戳、nonce 与签名
     */
    public WebhookSignedRequest sign(String rawBody) {
        String body = rawBody == null ? "" : rawBody;
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String signature = hmacHex(canonical(timestamp, nonce, body));
        return new WebhookSignedRequest(timestamp, nonce, signature);
    }

    /**
     * 校验入站回调的签名、时间窗与 nonce 唯一性。
     *
     * @param timestamp 请求头 {@code X-TaskForge-Timestamp}
     * @param nonce     请求头 {@code X-TaskForge-Nonce}
     * @param signature 请求头 {@code X-TaskForge-Signature}
     * @param rawBody   原始请求正文
     * @return 校验结果；非 {@link WebhookVerifyResult#VALID} 一律应拒绝
     */
    public WebhookVerifyResult verify(String timestamp, String nonce, String signature, String rawBody) {
        if (isBlank(timestamp) || isBlank(nonce) || isBlank(signature)) {
            return WebhookVerifyResult.INVALID_PARAMETER;
        }

        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return WebhookVerifyResult.INVALID_TIMESTAMP;
        }
        if (Math.abs(System.currentTimeMillis() - requestTime) > timeWindow.toMillis()) {
            return WebhookVerifyResult.INVALID_TIMESTAMP;
        }

        String body = rawBody == null ? "" : rawBody;
        String expected = hmacHex(canonical(timestamp, nonce, body));
        if (!constantTimeEquals(expected, signature)) {
            return WebhookVerifyResult.INVALID_SIGNATURE;
        }

        if (!nonceStore.claim(nonce, nonceTtl())) {
            return WebhookVerifyResult.REPLAY;
        }
        return WebhookVerifyResult.VALID;
    }

    private Duration nonceTtl() {
        return timeWindow.multipliedBy(2);
    }

    private static String canonical(String timestamp, String nonce, String rawBody) {
        return timestamp + NEWLINE + nonce + NEWLINE + rawBody;
    }

    private String hmacHex(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
