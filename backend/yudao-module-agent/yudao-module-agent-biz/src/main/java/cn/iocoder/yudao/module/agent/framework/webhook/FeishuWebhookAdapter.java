package cn.iocoder.yudao.module.agent.framework.webhook;

import cn.iocoder.yudao.module.agent.framework.notice.NoticeCard;
import cn.iocoder.yudao.module.agent.framework.notice.NoticeEvent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书自定义机器人 Webhook 适配器
 *
 * <p>把 {@link NoticeCard} 渲染为飞书 {@code msg_type=interactive} 交互卡片：
 * 头部按事件类型着色，正文用 {@code lark_md} 元素呈现任务字段。验收/打回按钮
 * 属于 TASK-NOTICE-04 的回调能力，本适配器只负责消息发送。</p>
 *
 * @author TaskForge
 */
@Component
public class FeishuWebhookAdapter implements WebhookAdapter {

    @Override
    public WebhookPlatform platform() {
        return WebhookPlatform.FEISHU;
    }

    @Override
    public Map<String, Object> render(NoticeCard card) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("msg_type", "interactive");

        Map<String, Object> cardBody = new LinkedHashMap<>();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("wide_screen_mode", true);
        cardBody.put("config", config);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("template", templateOf(card.getEvent()));
        Map<String, Object> title = new LinkedHashMap<>();
        title.put("tag", "plain_text");
        title.put("content", card.getStatus() + "：" + card.getTitle());
        header.put("title", title);
        cardBody.put("header", header);

        Map<String, Object> element = new LinkedHashMap<>();
        element.put("tag", "div");
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("tag", "lark_md");
        text.put("content", renderMarkdown(card));
        element.put("text", text);
        cardBody.put("elements", List.of(element));

        message.put("card", cardBody);
        return message;
    }

    private String templateOf(String event) {
        if (NoticeEvent.FAILED.getValue().equals(event)) {
            return "red";
        }
        if (NoticeEvent.MERGE_CONFLICT_PENDING_MANUAL.getValue().equals(event)) {
            return "orange";
        }
        return "blue";
    }

    String renderMarkdown(NoticeCard card) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(card.getStatus()).append("：").append(card.getTitle()).append("**").append('\n');
        sb.append("任务编号：").append(card.getTaskNo()).append('\n');
        sb.append("报告版本：").append(card.getReportVersion()).append('\n');
        sb.append('\n');
        WebhookMarkdown.appendFields(sb, card.getFields());
        return sb.toString();
    }

}
