package cn.iocoder.yudao.module.agent.service.ops;

import cn.iocoder.yudao.module.agent.enums.AgentAlertType;

/**
 * 补偿任务告警记录服务
 *
 * <p>补偿任务发现异常或完成恢复时，写入不可变告警记录，供运维定位与统计。</p>
 *
 * @author TaskForge
 */
public interface AgentAlertService {

    /**
     * 记录一条告警。
     *
     * @param type        告警类型
     * @param level       告警级别（INFO/WARN/ERROR）
     * @param taskNo      关联任务编号，可为空
     * @param resourceKey 关联资源标识，可为空
     * @param message     告警摘要
     * @param detail      脱敏后的告警详情，可为空
     */
    void record(AgentAlertType type, String level, String taskNo, String resourceKey,
                String message, String detail);

}
