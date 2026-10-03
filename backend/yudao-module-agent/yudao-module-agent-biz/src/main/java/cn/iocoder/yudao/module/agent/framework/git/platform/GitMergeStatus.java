package cn.iocoder.yudao.module.agent.framework.git.platform;

import java.util.Objects;

/**
 * 合并请求（MR/PR）状态查询结果。
 *
 * @param state     平台侧状态
 * @param mergeable 是否可无冲突合并；平台未返回时为 {@code null}
 * @param commitSha 合并提交 SHA（未合并时为空）
 * @param webUrl    Web 地址
 * @param title     标题
 * @author TaskForge
 */
public record GitMergeStatus(GitMergeState state, Boolean mergeable, String commitSha, String webUrl, String title) {

    public GitMergeStatus {
        Objects.requireNonNull(state, "state 不能为空");
        webUrl = webUrl == null ? "" : webUrl;
        title = title == null ? "" : title;
    }

    public boolean isMerged() {
        return state == GitMergeState.MERGED;
    }

}
