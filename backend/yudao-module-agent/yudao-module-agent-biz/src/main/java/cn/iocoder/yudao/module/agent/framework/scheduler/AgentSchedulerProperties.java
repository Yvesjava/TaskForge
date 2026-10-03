package cn.iocoder.yudao.module.agent.framework.scheduler;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 调度器与 Worker 租约相关配置
 *
 * <p>集中管理任务抢占后的租约时长，避免在业务代码中硬编码。</p>
 *
 * @author TaskForge
 */
@Data
@Component
@ConfigurationProperties(prefix = "yudao.agent.scheduler")
@Validated
public class AgentSchedulerProperties {

    /**
     * 抢占后 Worker 租约时长（分钟）
     */
    private long leaseMinutes = 5;

    /**
     * 租约 Hash 键在租约到期后的额外保留时长（秒），用于租约异常快速告警
     */
    private long leaseBufferSeconds = 60;

    /**
     * Worker 心跳键 TTL（秒），用于判断 Worker 是否仍存活
     */
    private long heartbeatTtlSeconds = 90;

    /**
     * 取消信号键 TTL（秒），覆盖租约与心跳缓冲窗口，保证旧 Worker 能在有限时间内停止
     */
    private long cancelTtlSeconds = 300;

    /**
     * 租约过期扫描每次处理的最大任务数，用于有界批次恢复，避免单次扫描拖垮数据库
     */
    private int recoveryBatchSize = 50;

}
