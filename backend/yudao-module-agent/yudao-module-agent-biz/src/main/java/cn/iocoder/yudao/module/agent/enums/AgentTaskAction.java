package cn.iocoder.yudao.module.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态动作枚举
 *
 * <p>动作值同时作为 {@code agent_task_operation_log.action} 的写入值。
 * 只有在本枚举中登记的动作才允许触发状态转换。</p>
 *
 * @author TaskForge
 */
@Getter
@AllArgsConstructor
public enum AgentTaskAction {

    PAUSE("PAUSE", "暂停"),
    RESUME("RESUME", "恢复"),
    CANCEL("CANCEL", "取消"),
    RESET("RESET", "重置"),
    RE_ENQUEUE("RE_ENQUEUE", "重新入队"),
    CLONE_RE_ENQUEUE("CLONE_RE_ENQUEUE", "克隆重投"),
    CLAIM("CLAIM", "抢占"),
    SELF_VERIFY_PASS("SELF_VERIFY_PASS", "自验通过"),
    TIMEOUT("TIMEOUT", "执行超时"),
    ERROR("ERROR", "执行失败"),
    HEARTBEAT_EXPIRED("HEARTBEAT_EXPIRED", "租约过期"),
    ACCEPT("ACCEPT", "验收通过"),
    REJECT("REJECT", "打回"),
    MERGE_PASS("MERGE_PASS", "合并成功"),
    MERGE_CONFLICT("MERGE_CONFLICT", "合并冲突"),
    MERGE_RETRY("MERGE_RETRY", "重试合并"),
    DELETE("DELETE", "软删除"),
    CLEANUP_PASS("CLEANUP_PASS", "重置清理完成"),
    EDIT("EDIT", "编辑任务文档");

    private final String value;
    private final String label;

}
