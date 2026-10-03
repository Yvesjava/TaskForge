package cn.iocoder.yudao.module.agent.framework.notice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 通知数据脱敏器
 *
 * <p>通知卡片在写出前统一经过本类处理，禁止把 Git 凭证、Token、Cookie、
 * Authorization 头、私钥或密码写入卡片。规则采用“宁可多打码、不可漏秘密”的策略。</p>
 *
 * @author TaskForge
 */
public final class NoticeSecretRedactor {

    private static final String MASK = "***";

    private static final List<RedactionRule> RULES = List.of(
            // URL 中的 userinfo（scheme://user[:password]@host）
            new RedactionRule(
                    Pattern.compile("(?i)(\\b[a-z][a-z0-9+.-]*://)[^/@\\s]+@"),
                    "$1" + MASK + "@"),
            // Authorization / Proxy-Authorization 头或键值对
            new RedactionRule(
                    Pattern.compile("(?i)(\\b(?:authorization|proxy-authorization)\\s*[:=]\\s*)"
                            + "(?:(?:bearer|basic|digest|token)\\s+)?(?:\"[^\"]*\"|'[^']*'|[^\\s,;\"']+)"),
                    "$1" + MASK),
            // 常见秘密键值对（password/token/api-key/private-key 等）
            new RedactionRule(
                    Pattern.compile("(?i)(\\b(?:password|passwd|pwd|secret|token|access[_-]?token"
                            + "|api[_-]?key|access[_-]?key|private[_-]?key)\\b\\s*[:=]\\s*)"
                            + "(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)"),
                    "$1" + MASK),
            // AWS Access Key ID
            new RedactionRule(Pattern.compile("\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b"), MASK),
            // PEM 私钥块
            new RedactionRule(
                    Pattern.compile("(?s)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----"),
                    MASK)
    );

    private NoticeSecretRedactor() {
    }

    public static String redact(String value) {
        if (value == null) {
            return null;
        }
        String result = value;
        for (RedactionRule rule : RULES) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return result;
    }

    /**
     * 递归脱敏任意对象，覆盖字符串、Map 和 Iterable，便于处理卡片中的嵌套结构。
     */
    public static Object sanitize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return redact(text);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sanitized.put(sanitize(entry.getKey()), sanitize(entry.getValue()));
            }
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : iterable) {
                sanitized.add(sanitize(item));
            }
            return sanitized;
        }
        return value;
    }

    private record RedactionRule(Pattern pattern, String replacement) {
    }

}
