package cn.iocoder.yudao.module.agent.framework.webhook;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * 卡片回调令牌
 *
 * <p>将一次卡片回调绑定到「任务编号 + 动作 + 报告版本」，避免 Webhook 回调绕过
 * 控制面权限，任意推进任务状态。令牌由发送端在卡片按钮 {@code value} 中写入，
 * 回调时由 {@code AgentNoticeCallbackService} 重新计算并常量时间比较。</p>
 *
 * <p>令牌使用 HMAC-SHA256，共享密钥与 {@link WebhookSignature} 一致，来自
 * {@link WebhookProperties#getSecret()}。令牌本身确定且不可逆，因此同时可作为
 * 本次回调的幂等键，重复投递不会重复执行状态转换。</p>
 *
 * @author TaskForge
 */
@Component
public class WebhookCallbackToken {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String NEWLINE = "\n";

    private final String secret;

    public WebhookCallbackToken(WebhookProperties properties) {
        Objects.requireNonNull(properties, "properties 不能为空");
        this.secret = properties.getSecret() == null ? "" : properties.getSecret();
    }

    /**
     * 为指定任务、动作与报告版本签发回调令牌。
     */
    public String issue(String taskNo, String action, String reportVersion) {
        if (isBlank(taskNo) || isBlank(action) || isBlank(reportVersion)) {
            throw new IllegalArgumentException("taskNo、action 与 reportVersion 不能为空");
        }
        return hmacHex(canonical(taskNo, action, reportVersion));
    }

    /**
     * 校验回调令牌是否与任务、动作、报告版本匹配。
     */
    public boolean matches(String token, String taskNo, String action, String reportVersion) {
        if (isBlank(token) || isBlank(taskNo) || isBlank(action) || isBlank(reportVersion)) {
            return false;
        }
        return constantTimeEquals(issue(taskNo, action, reportVersion), token);
    }

    private static String canonical(String taskNo, String action, String reportVersion) {
        return taskNo + NEWLINE + action + NEWLINE + reportVersion;
    }

    private String hmacHex(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("回调令牌 HMAC 计算失败", e);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
