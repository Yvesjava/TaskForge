package cn.iocoder.yudao.module.agent.framework.webhook;

/**
 * Webhook HMAC-SHA256 签名请求头契约
 *
 * <p>发送卡片时写入这三个请求头，接收回调时按同样的头名读取并校验。
 * 签名内容为 {@code timestamp + "\n" + nonce + "\n" + rawBody}。</p>
 *
 * @author TaskForge
 */
public final class WebhookSignatureHeaders {

    /**
     * 签名时间戳（Unix 毫秒）
     */
    public static final String TIMESTAMP = "X-TaskForge-Timestamp";

    /**
     * 签名随机数，用于回放防护，单个 nonce 只能使用一次
     */
    public static final String NONCE = "X-TaskForge-Nonce";

    /**
     * HMAC-SHA256 签名（十六进制小写）
     */
    public static final String SIGNATURE = "X-TaskForge-Signature";

    private WebhookSignatureHeaders() {
    }

}
