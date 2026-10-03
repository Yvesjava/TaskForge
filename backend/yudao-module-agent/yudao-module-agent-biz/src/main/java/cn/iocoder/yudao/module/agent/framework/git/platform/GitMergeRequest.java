package cn.iocoder.yudao.module.agent.framework.git.platform;

/**
 * 创建合并请求（MR/PR）的统一输入。
 *
 * @param title        标题
 * @param description  描述（可为空）
 * @param sourceBranch 源分支
 * @param targetBranch 目标分支
 * @author TaskForge
 */
public record GitMergeRequest(String title, String description, String sourceBranch, String targetBranch) {

    public GitMergeRequest {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title 不能为空");
        }
        if (sourceBranch == null || sourceBranch.isBlank()) {
            throw new IllegalArgumentException("sourceBranch 不能为空");
        }
        if (targetBranch == null || targetBranch.isBlank()) {
            throw new IllegalArgumentException("targetBranch 不能为空");
        }
        description = description == null ? "" : description;
    }

    public static GitMergeRequest of(String title, String description, String sourceBranch, String targetBranch) {
        return new GitMergeRequest(title, description, sourceBranch, targetBranch);
    }

}
