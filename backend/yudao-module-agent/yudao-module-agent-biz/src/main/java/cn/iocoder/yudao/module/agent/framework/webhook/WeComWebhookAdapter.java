package cn.iocoder.yudao.module.agent.framework.webhook;

import cn.iocoder.yudao.module.agent.framework.notice.NoticeCard;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业微信群机器人 Webhook 适配器
 *
 * <p>把 {@link NoticeCard} 渲染为企业微信 {@code msgtype=markdown} 消息。
 * 企业微信 markdown 仅支持有限语法，因此只使用标题、引用、加粗与列表。</p>
 *
 * @author TaskForge
 */
@Component
public class WeComWebhookAdapter implements WebhookAdapter {

    @Override
    public WebhookPlatform platform() {
        return WebhookPlatform.WECOM;
    }

    @Override
    public Map<String, Object> render(NoticeCard card) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("msgtype", "markdown");
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("content", renderMarkdown(card));
        message.put("markdown", markdown);
        return message;
    }

    String renderMarkdown(NoticeCard card) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(card.getStatus()).append("：").append(card.getTitle()).append('\n');
        sb.append("> 任务编号：").append(card.getTaskNo()).append('\n');
        sb.append("> 报告版本：").append(card.getReportVersion()).append('\n');
        sb.append('\n');
        WebhookMarkdown.appendFields(sb, card.getFields());
        return sb.toString();
    }

}
