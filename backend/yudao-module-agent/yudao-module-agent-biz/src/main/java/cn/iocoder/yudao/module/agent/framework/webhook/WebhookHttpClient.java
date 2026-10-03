package cn.iocoder.yudao.module.agent.framework.webhook;

/**
 * Webhook HTTP 投递客户端
 *
 * <p>抽象真实网络调用，便于在单元测试中注入假客户端，验证 {@link WebhookSender}
 * 的发送、超时与错误重试逻辑。真实实现见 {@link JdkWebhookClient}。</p>
 *
 * @author TaskForge
 */
@FunctionalInterface
public interface WebhookHttpClient {

    /**
     * 同步投递一次 Webhook 请求，方法不得抛出异常，失败信息统一放入 {@link WebhookResponse}。
     *
     * @param request 投递请求
     * @return 投递响应
     */
    WebhookResponse post(WebhookRequest request);

}
