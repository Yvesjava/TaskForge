package cn.iocoder.yudao.module.agent.framework.webhook;

/**
 * 通知 Webhook 平台枚举
 *
 * <p>目前支持企业微信与飞书两种群机器人 Webhook。后续新增平台时，
 * 需同时实现 {@link WebhookAdapter} 并交由 {@link WebhookAdapterRegistry} 路由。</p>
 *
 * @author TaskForge
 */
public enum WebhookPlatform {

    WECOM("企业微信"),
    FEISHU("飞书");

    private final String label;

    WebhookPlatform(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
