package cn.iocoder.yudao.module.agent.service.workspace;

/**
 * 可追踪的分支操作结果
 *
 * <p>分支创建、基线校验与清理的每一步都返回该结构，把命名（{@code branch}）、
 * 基线（{@code baseline} + {@code commit}）与远端动作（{@code remote} + {@code action}）
 * 固化在返回值中，供上层写入操作日志或指标。
 *
 * @param action      操作类型（见 {@link TaskBranchManager} 中的 ACTION_* 常量）
 * @param projectCode 项目代号
 * @param branch      操作涉及的分支名
 * @param baseline    基线分支名（无基线语义时为空）
 * @param remote      远端名（无远端动作时为空）
 * @param commit      操作后的提交哈希（无提交语义时为空）
 * @author TaskForge
 */
public record BranchOperation(
        String action,
        String projectCode,
        String branch,
        String baseline,
        String remote,
        String commit) {
}
