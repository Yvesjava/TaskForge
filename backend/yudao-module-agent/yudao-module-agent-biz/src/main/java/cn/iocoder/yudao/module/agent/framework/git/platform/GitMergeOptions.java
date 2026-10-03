package cn.iocoder.yudao.module.agent.framework.git.platform;

/**
 * 执行合并时的选项。
 *
 * @param strategy           合并策略
 * @param removeSourceBranch 合并成功后是否删除源分支
 * @author TaskForge
 */
public record GitMergeOptions(GitMergeStrategy strategy, boolean removeSourceBranch) {

    public GitMergeOptions {
        if (strategy == null) {
            throw new IllegalArgumentException("strategy 不能为空");
        }
    }

    public static GitMergeOptions of(GitMergeStrategy strategy, boolean removeSourceBranch) {
        return new GitMergeOptions(strategy, removeSourceBranch);
    }

    public static GitMergeOptions fastForward() {
        return new GitMergeOptions(GitMergeStrategy.FAST_FORWARD, true);
    }

    public static GitMergeOptions squash() {
        return new GitMergeOptions(GitMergeStrategy.SQUASH, true);
    }

}
