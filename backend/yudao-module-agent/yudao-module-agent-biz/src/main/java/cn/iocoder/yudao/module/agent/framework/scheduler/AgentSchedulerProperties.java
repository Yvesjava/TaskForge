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

}
