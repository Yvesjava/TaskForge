package cn.iocoder.yudao.module.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 任务状态枚举
 *
 * <p>状态取值与数据库 {@code agent_task.status} 字段一致，是状态机的唯一事实来源。
 * 未列出的组合一律视为非法转换。</p>
 *
 * @author TaskForge
 */
@Getter
@AllArgsConstructor
public enum AgentTaskStatus {

    PENDING("PENDING", "待执行"),
    PAUSED("PAUSED", "已暂停"),
    RUNNING("RUNNING", "执行中"),
    WAITING_ACCEPTANCE("WAITING_ACCEPTANCE", "待验收"),
    ACCEPTED("ACCEPTED", "已验收"),
    COMPLETED("COMPLETED", "已完成"),
    REJECTED("REJECTED", "已打回"),
    CANCELED("CANCELED", "已取消"),
    DELETED("DELETED", "已软删除"),
    FAILED("FAILED", "执行失败"),
    RESETTING("RESETTING", "重置中"),
    MERGE_CONFLICT_PENDING_MANUAL("MERGE_CONFLICT_PENDING_MANUAL", "合并冲突待处理");

    /**
     * 数据库存储值
     */
    private final String value;
    /**
     * 状态中文名
     */
    private final String label;

    public static AgentTaskStatus valueOfCode(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.value.equals(value))
                .findFirst()
                .orElse(null);
    }

}
