package cn.iocoder.yudao.module.agent.framework.webhook;

/**
 * Webhook 回调签名校验结果
 *
 * <p>只有 {@link #VALID} 表示可以继续处理回调，其余结果都必须拒绝。</p>
 *
 * @author TaskForge
 */
public enum WebhookVerifyResult {

    VALID("通过"),
    INVALID_PARAMETER("签名参数缺失或为空"),
    INVALID_TIMESTAMP("时间戳非法或超出时间窗"),
    INVALID_SIGNATURE("签名不匹配"),
    REPLAY("重复回放");

    private final String description;

    WebhookVerifyResult(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isValid() {
        return this == VALID;
    }

}
