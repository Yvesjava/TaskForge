package cn.iocoder.yudao.module.agent.framework.git.platform;

import java.util.Objects;

/**
 * 执行合并的结果。
 *
 * @param merged    是否合并成功
 * @param state     合并后的平台侧状态
 * @param commitSha 合并提交 SHA（失败时为空）
 * @param webUrl    Web 地址
 * @param message   平台返回的提示/错误信息
 * @author TaskForge
 */
public record GitMergeResult(boolean merged, GitMergeState state, String commitSha, String webUrl, String message) {

    public GitMergeResult {
        Objects.requireNonNull(state, "state 不能为空");
        commitSha = commitSha == null ? "" : commitSha;
        webUrl = webUrl == null ? "" : webUrl;
        message = message == null ? "" : message;
    }

    public static GitMergeResult success(String commitSha, String webUrl) {
        return new GitMergeResult(true, GitMergeState.MERGED, commitSha, webUrl, "");
    }

    public static GitMergeResult failure(GitMergeState state, String webUrl, String message) {
        return new GitMergeResult(false, state, "", webUrl, message);
    }

}
