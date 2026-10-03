package cn.iocoder.yudao.module.agent.framework.ops;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/**
 * 补偿任务与告警配置
 *
 * <p>集中管理残留工作区、过期租约与失败通知三类补偿的批次与重试参数。</p>
 *
 * @author TaskForge
 */
@Data
@Component
@ConfigurationProperties(prefix = "yudao.agent.compensation")
@Validated
public class AgentCompensationProperties {

    /**
     * 每轮补偿最多处理的失败通知 Outbox 条数，用于有界批次避免拖垮数据库
     */
    @Min(1)
    private int noticeDispatchBatchSize = 50;

    /**
     * 失败通知默认最大重试次数（Outbox 未单独指定时的兜底值）
     */
    @Min(0)
    private int noticeMaxRetries = 3;

    /**
     * 失败通知重发退避间隔（秒）
     */
    @Min(0)
    private long noticeRetryBackoffSeconds = 60;

}
