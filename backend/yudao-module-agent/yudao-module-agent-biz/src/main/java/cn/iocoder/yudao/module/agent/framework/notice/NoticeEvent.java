package cn.iocoder.yudao.module.agent.framework.notice;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 通知事件枚举
 *
 * <p>通知事件与任务状态一一对应，是通知卡片路由的唯一事实来源。
 * 待验收、执行失败、合并冲突三类卡片的稳定字段由 {@link NoticeCardFields} 固化。</p>
 *
 * @author TaskForge
 */
@Getter
@AllArgsConstructor
public enum NoticeEvent {

    WAITING_ACCEPTANCE("WAITING_ACCEPTANCE", "待验收"),
    FAILED("FAILED", "执行失败"),
    MERGE_CONFLICT_PENDING_MANUAL("MERGE_CONFLICT_PENDING_MANUAL", "合并冲突待处理");

    /**
     * 事件编码，与任务状态值一致
     */
    private final String value;
    /**
     * 事件中文名
     */
    private final String label;

    public static NoticeEvent valueOfCode(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(event -> event.value.equals(value))
                .findFirst()
                .orElse(null);
    }

}
