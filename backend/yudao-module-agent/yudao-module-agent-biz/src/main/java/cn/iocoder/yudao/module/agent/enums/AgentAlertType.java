package cn.iocoder.yudao.module.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 补偿任务告警类型枚举
 *
 * <p>与补偿任务的三个维度一一对应：残留工作区、过期租约、失败通知。</p>
 *
 * @author TaskForge
 */
@Getter
@AllArgsConstructor
public enum AgentAlertType {

    RESIDUAL_WORKSPACE("RESIDUAL_WORKSPACE", "残留工作区"),
    EXPIRED_LEASE("EXPIRED_LEASE", "过期租约"),
    FAILED_NOTICE("FAILED_NOTICE", "失败通知");

    /**
     * 告警类型编码
     */
    private final String value;
    /**
     * 告警类型中文名
     */
    private final String label;

}
