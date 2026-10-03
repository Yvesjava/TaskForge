package cn.iocoder.yudao.module.agent.framework.webhook;

import cn.iocoder.yudao.module.agent.framework.notice.NoticeCard;

import java.util.Map;

/**
 * 通知 Webhook 适配器契约
 *
 * <p>每种平台负责把统一的 {@link NoticeCard} 翻译成该平台群机器人 Webhook
 * 可识别的 JSON 消息结构。发送、超时与错误重试由 {@link WebhookSender} 统一负责，
 * 适配器只关心平台差异，不直接接触网络。</p>
 *
 * @author TaskForge
 */
public interface WebhookAdapter {

    /**
     * 适配器支持的平台。
     */
    WebhookPlatform platform();

    /**
     * 把通知卡片渲染为该平台的 Webhook 消息 payload。
     *
     * @param card 通知卡片（已脱敏）
     * @return 平台可识别的 JSON 消息结构
     */
    Map<String, Object> render(NoticeCard card);

}
