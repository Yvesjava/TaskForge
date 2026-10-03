package cn.iocoder.yudao.module.agent.framework.webhook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通知卡片字段到 Markdown 的公共渲染工具
 *
 * <p>企业微信与飞书适配器共享字段中文名映射与列表/映射格式化逻辑，
 * 保证同一张 {@code NoticeCard} 在两种平台上的可读字段表达保持一致。</p>
 *
 * @author TaskForge
 */
final class WebhookMarkdown {

    private WebhookMarkdown() {
    }

    static String labelOf(String key) {
        return switch (key) {
            case "branches" -> "分支";
            case "changedFiles" -> "变更文件";
            case "diffStat" -> "Diff 统计";
            case "testSummary" -> "测试摘要";
            case "retryTimes" -> "重试次数";
            case "costMs" -> "耗时(ms)";
            case "failedAt" -> "失败时间";
            case "errorSummary" -> "错误摘要";
            case "logTail" -> "日志尾部";
            case "conflicts" -> "冲突";
            default -> key;
        };
    }

    static String inline(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                parts.add(entry.getKey() + ": " + (entry.getValue() == null ? "" : entry.getValue()));
            }
            return String.join("，", parts);
        }
        return String.valueOf(value);
    }

    static void appendFields(StringBuilder sb, Map<String, Object> fields) {
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            sb.append("**").append(labelOf(entry.getKey())).append("**").append('\n');
            if (value instanceof List<?> list) {
                if (list.isEmpty()) {
                    sb.append("（无）").append('\n');
                } else {
                    for (Object item : list) {
                        sb.append("- ").append(inline(item)).append('\n');
                    }
                }
            } else {
                sb.append(inline(value)).append('\n');
            }
            sb.append('\n');
        }
    }

}
