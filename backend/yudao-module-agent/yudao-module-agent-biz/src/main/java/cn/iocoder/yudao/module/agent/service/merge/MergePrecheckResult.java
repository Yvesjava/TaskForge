package cn.iocoder.yudao.module.agent.service.merge;

import java.util.List;

/**
 * 多仓合并预检聚合结果。
 *
 * <p>仅当所有仓库都通过预检时 {@link #passed()} 才为 {@code true}；任一仓冲突、
 * 检查失败或平台错误都会让整体判定为未通过，从而保证合并前“全有或全无”。</p>
 *
 * @param items 各仓库预检结果，顺序与预检输入一致
 * @author TaskForge
 */
public record MergePrecheckResult(List<MergePrecheckItemResult> items) {

    public MergePrecheckResult {
        items = List.copyOf(items);
    }

    public boolean passed() {
        return !items.isEmpty() && items.stream().allMatch(MergePrecheckItemResult::passed);
    }

    public List<MergePrecheckItemResult> failures() {
        return items.stream().filter(item -> !item.passed()).toList();
    }

}
