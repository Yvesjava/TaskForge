package cn.iocoder.yudao.module.agent.framework.webhook;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.yudao.module.agent.framework.webhook.WebhookVerifyResult.INVALID_PARAMETER;
import static cn.iocoder.yudao.module.agent.framework.webhook.WebhookVerifyResult.INVALID_SIGNATURE;
import static cn.iocoder.yudao.module.agent.framework.webhook.WebhookVerifyResult.INVALID_TIMESTAMP;
import static cn.iocoder.yudao.module.agent.framework.webhook.WebhookVerifyResult.REPLAY;
import static cn.iocoder.yudao.module.agent.framework.webhook.WebhookVerifyResult.VALID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-NOTICE-03 HMAC-SHA256 签名、时间窗与回放防护测试。
 *
 * <p>签名算法在测试中独立复算，避免与生产实现同源而互相掩盖错误。</p>
 */
class WebhookSignatureTest {

    private static final String SECRET = "taskforge-webhook-secret";
    private static final String BODY = "{\"taskNo\":\"TASK-20261003-001\",\"action\":\"accept\"}";

    @Test
    void sign_producesIndependentHmacOverCanonicalString() throws Exception {
        WebhookSignature signature = signature(new InMemoryNonceStore());

        WebhookSignedRequest signed = signature.sign(BODY);

        String canonical = signed.timestamp() + "\n" + signed.nonce() + "\n" + BODY;
        assertThat(signed.signature()).isEqualTo(hmacSha256Hex(SECRET, canonical));
        assertThat(signed.headers())
                .containsEntry(WebhookSignatureHeaders.TIMESTAMP, signed.timestamp())
                .containsEntry(WebhookSignatureHeaders.NONCE, signed.nonce())
                .containsEntry(WebhookSignatureHeaders.SIGNATURE, signed.signature());
    }

    @Test
    void verify_acceptsFreshValidSignature() {
        WebhookSignature signature = signature(new InMemoryNonceStore());
        WebhookSignedRequest signed = signature.sign(BODY);

        assertThat(signature.verify(signed.timestamp(), signed.nonce(), signed.signature(), BODY))
                .isEqualTo(VALID);
    }

    @Test
    void verify_rejectsMissingParameters() {
        WebhookSignature signature = signature(new InMemoryNonceStore());

        assertThat(signature.verify(null, "nonce", "signature", BODY))
                .isEqualTo(INVALID_PARAMETER);
        assertThat(signature.verify("123", "", "signature", BODY))
                .isEqualTo(INVALID_PARAMETER);
        assertThat(signature.verify("123", "nonce", " ", BODY))
                .isEqualTo(INVALID_PARAMETER);
    }

    @Test
    void verify_rejectsNonNumericTimestamp() {
        WebhookSignature signature = signature(new InMemoryNonceStore());

        assertThat(signature.verify("not-a-number", UUID.randomUUID().toString(), "signature", BODY))
                .isEqualTo(INVALID_TIMESTAMP);
    }

    @Test
    void verify_rejectsExpiredAndFutureTimestamp() throws Exception {
        WebhookSignature signature = signature(new InMemoryNonceStore());

        String expired = String.valueOf(System.currentTimeMillis() - Duration.ofMinutes(6).toMillis());
        String expiredNonce = nonce();
        assertThat(signature.verify(expired, expiredNonce, hmac(expired, expiredNonce, BODY), BODY))
                .isEqualTo(INVALID_TIMESTAMP);

        String future = String.valueOf(System.currentTimeMillis() + Duration.ofMinutes(6).toMillis());
        String futureNonce = nonce();
        assertThat(signature.verify(future, futureNonce, hmac(future, futureNonce, BODY), BODY))
                .isEqualTo(INVALID_TIMESTAMP);
    }

    @Test
    void verify_acceptsTimestampInsideWindow() throws Exception {
        WebhookSignature signature = signature(new InMemoryNonceStore());
        String timestamp = String.valueOf(System.currentTimeMillis() - Duration.ofMinutes(1).toMillis());
        String nonce = nonce();

        assertThat(signature.verify(timestamp, nonce, hmac(timestamp, nonce, BODY), BODY))
                .isEqualTo(VALID);
    }

    @Test
    void verify_rejectsTamperedBodyAndWrongSignature() {
        WebhookSignature signature = signature(new InMemoryNonceStore());
        WebhookSignedRequest signed = signature.sign(BODY);

        assertThat(signature.verify(signed.timestamp(), signed.nonce(), signed.signature(), "{\"tampered\":true}"))
                .isEqualTo(INVALID_SIGNATURE);
        assertThat(signature.verify(signed.timestamp(), signed.nonce(), "deadbeef", BODY))
                .isEqualTo(INVALID_SIGNATURE);
    }

    @Test
    void verify_rejectsReplayedNonce() {
        WebhookSignature signature = signature(new InMemoryNonceStore());
        WebhookSignedRequest signed = signature.sign(BODY);

        assertThat(signature.verify(signed.timestamp(), signed.nonce(), signed.signature(), BODY))
                .isEqualTo(VALID);
        assertThat(signature.verify(signed.timestamp(), signed.nonce(), signed.signature(), BODY))
                .isEqualTo(REPLAY);
    }

    @Test
    void verify_doesNotConsumeNonceWhenSignatureIsInvalid() {
        WebhookSignature signature = signature(new InMemoryNonceStore());
        WebhookSignedRequest signed = signature.sign(BODY);

        assertThat(signature.verify(signed.timestamp(), signed.nonce(), "wrong", BODY))
                .isEqualTo(INVALID_SIGNATURE);
        assertThat(signature.verify(signed.timestamp(), signed.nonce(), signed.signature(), BODY))
                .isEqualTo(VALID);
    }

    private WebhookSignature signature(WebhookNonceStore nonceStore) {
        WebhookProperties properties = new WebhookProperties();
        properties.setSecret(SECRET);
        return new WebhookSignature(properties, nonceStore);
    }

    private String hmac(String timestamp, String nonce, String body) throws Exception {
        return hmacSha256Hex(SECRET, timestamp + "\n" + nonce + "\n" + body);
    }

    private String nonce() {
        return UUID.randomUUID().toString();
    }

    private static String hmacSha256Hex(String secret, String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)))
                .toLowerCase(Locale.ROOT);
    }

    private static final class InMemoryNonceStore implements WebhookNonceStore {

        private final Set<String> claimed = ConcurrentHashMap.newKeySet();

        @Override
        public boolean claim(String nonce, Duration ttl) {
            return claimed.add(nonce);
        }

    }

}
