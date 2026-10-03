package cn.iocoder.yudao.module.agent.framework.webhook;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已完成 HMAC-SHA256 签名的出站 Webhook 请求头
 *
 * <p>由 {@link WebhookSignature#sign(String)} 生成，供 HTTP 客户端写入请求头。
 * 请求头名与 {@link WebhookSignatureHeaders} 保持一致。</p>
 *
 * @param timestamp Unix 毫秒时间戳
 * @param nonce     一次性随机数
 * @param signature HMAC-SHA256 签名（十六进制小写）
 * @author TaskForge
 */
public record WebhookSignedRequest(String timestamp, String nonce, String signature) {

    public WebhookSignedRequest {
        if (timestamp == null || timestamp.isBlank()) {
            throw new IllegalArgumentException("timestamp 不能为空");
        }
        if (nonce == null || nonce.isBlank()) {
            throw new IllegalArgumentException("nonce 不能为空");
        }
        if (signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("signature 不能为空");
        }
    }

    /**
     * 转为不可变请求头映射，便于 HTTP 客户端一次性添加。
     */
    public Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(WebhookSignatureHeaders.TIMESTAMP, timestamp);
        headers.put(WebhookSignatureHeaders.NONCE, nonce);
        headers.put(WebhookSignatureHeaders.SIGNATURE, signature);
        return Map.copyOf(headers);
    }

}
