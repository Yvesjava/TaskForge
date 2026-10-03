package cn.iocoder.yudao.module.agent.framework.webhook;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

import java.time.Duration;

/**
 * 通知 Webhook 发送配置
 *
 * <p>集中管理连接超时、读取超时与失败重试参数，避免在业务代码中硬编码网络参数。</p>
 *
 * @author TaskForge
 */
@Data
@Component
@ConfigurationProperties(prefix = "yudao.agent.webhook")
@Validated
public class WebhookProperties {

    /**
     * 建立 TCP 连接的超时时间
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 单次投递的读取/整体超时时间，超时后按可重试失败处理
     */
    private Duration readTimeout = Duration.ofSeconds(10);

    /**
     * 失败后最大重试次数（不含首次发送）；仅超时、连接错误与 5xx 会触发重试
     */
    @Min(0)
    private int maxRetries = 3;

    /**
     * 每次重试前的退避基准（毫秒），实际等待为 {@code retryBackoffMillis * 第几次重试}；
     * 为 0 时立即重试。
     */
    @Min(0)
    private long retryBackoffMillis = 500;

}
