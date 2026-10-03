package cn.iocoder.yudao.module.agent.service.ops;

import java.util.Map;

/**
 * 通知投递 Outbox 服务
 *
 * <p>把失败通知持久化为 Outbox 记录，并提供跨运行周期的到期重发能力，
 * 供补偿任务调用。重发成功置为 {@code SENT}，仍失败则推进重试并记录错误。</p>
 *
 * @author TaskForge
 */
public interface AgentNoticeOutboxService {

    /**
     * 记录一条待投递/失败待重试的通知。
     *
     * @param taskNo     任务编号
     * @param event      通知事件
     * @param platform   通知平台
     * @param webhookUrl 群机器人 Webhook 地址
     * @param payload    已渲染的消息 JSON
     * @param maxRetries 最大重试次数
     */
    void record(String taskNo, String event, String platform, String webhookUrl,
                Map<String, Object> payload, int maxRetries);

    /**
     * 处理一批到期可重试的通知。
     *
     * @param batchSize 单轮最大处理条数
     * @return 本轮投递结果
     */
    AgentNoticeDispatchResult dispatchDue(int batchSize);

}
