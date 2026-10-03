package cn.iocoder.yudao.module.agent.service.merge;

import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeRequestRef;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeState;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeStatus;

/**
 * 单个仓库的合并预检结果。
 *
 * @param repoKey   仓库标识
 * @param ref       预检期间解析出的 MR/PR 引用；平台调用失败且未创建时为 {@code null}
 * @param passed    是否通过预检
 * @param failure   未通过时的失败原因；通过时为 {@code null}
 * @param state     平台侧 MR/PR 状态
 * @param mergeable 平台返回的可合并性；平台未返回时为 {@code null}
 * @param message   补充说明（已合并、冲突原因或错误信息）
 * @author TaskForge
 */
public record MergePrecheckItemResult(String repoKey, GitMergeRequestRef ref, boolean passed,
                                      MergePrecheckFailure failure, GitMergeState state,
                                      Boolean mergeable, String message) {

    public MergePrecheckItemResult {
        if (repoKey == null || repoKey.isBlank()) {
            throw new IllegalArgumentException("repoKey 不能为空");
        }
        message = message == null ? "" : message;
        if (passed && failure != null) {
            throw new IllegalArgumentException("通过预检的仓库不能带有失败原因");
        }
        if (!passed && failure == null) {
            throw new IllegalArgumentException("未通过预检的仓库必须带有失败原因");
        }
    }

    public static MergePrecheckItemResult passed(String repoKey, GitMergeRequestRef ref,
                                                 GitMergeStatus status, String message) {
        return new MergePrecheckItemResult(repoKey, ref, true, null,
                status.state(), status.mergeable(), message);
    }

    public static MergePrecheckItemResult failed(String repoKey, GitMergeRequestRef ref,
                                                 MergePrecheckFailure failure, GitMergeState state,
                                                 Boolean mergeable, String message) {
        return new MergePrecheckItemResult(repoKey, ref, false, failure, state, mergeable, message);
    }

}
