package cn.iocoder.yudao.module.agent.framework.git.platform;

/**
 * 合并请求（MR/PR）引用。
 *
 * @param number 平台内的 MR/PR 编号（GitLab iid / GitHub、Gitea number）
 * @param webUrl Web 地址（可为空）
 * @author TaskForge
 */
public record GitMergeRequestRef(long number, String webUrl) {

    public GitMergeRequestRef {
        if (number <= 0) {
            throw new IllegalArgumentException("number 必须大于 0");
        }
        webUrl = webUrl == null ? "" : webUrl;
    }

}
