package cn.iocoder.yudao.module.agent.framework.secret;

import cn.iocoder.yudao.module.agent.framework.notice.NoticeSecretRedactor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 通用日志/文本脱敏器
 *
 * <p>在 {@link NoticeSecretRedactor} 的规则之上增加“已知秘密值替换”：先把已经通过
 * {@link SecretManager} 解析出的秘密字面量整体替换为 {@code ***}，再执行 URL
 * userinfo、Authorization 头、秘密键值对、AWS Key 与 PEM 私钥等规则脱敏。字符串
 * 使用字面量替换，秘密值中的正则元字符不会被当作表达式处理。</p>
 *
 * @author TaskForge
 */
public final class SecretRedactor {

    private static final String MASK = "***";

    /**
     * 仅对长度不小于该值的秘密做字面量替换，避免过短片段把普通文本误打成码。
     */
    private static final int MIN_SECRET_LENGTH = 4;

    private SecretRedactor() {
    }

    public static String redact(String value) {
        return redact(value, List.of());
    }

    public static String redact(String value, Collection<String> knownSecrets) {
        if (value == null) {
            return null;
        }
        String result = value;
        for (String secret : normalizedSecrets(knownSecrets)) {
            result = result.replace(secret, MASK);
        }
        return NoticeSecretRedactor.redact(result);
    }

    /**
     * 脱敏命令参数列表，返回一份新的列表，不修改原列表。
     */
    public static List<String> redactArguments(Collection<String> arguments, Collection<String> knownSecrets) {
        List<String> redacted = new ArrayList<>();
        if (arguments == null) {
            return redacted;
        }
        for (String argument : arguments) {
            redacted.add(redact(argument, knownSecrets));
        }
        return redacted;
    }

    /**
     * 生成异常的脱敏描述：返回脱敏后的消息，消息为空时返回异常类名。
     */
    public static String describe(Throwable throwable, Collection<String> knownSecrets) {
        if (throwable == null) {
            return "";
        }
        String message = throwable.getMessage() == null
                ? throwable.getClass().getSimpleName() : throwable.getMessage();
        return redact(message, knownSecrets);
    }

    private static List<String> normalizedSecrets(Collection<String> knownSecrets) {
        if (knownSecrets == null || knownSecrets.isEmpty()) {
            return List.of();
        }
        return knownSecrets.stream()
                .filter(secret -> secret != null && secret.length() >= MIN_SECRET_LENGTH)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

}
