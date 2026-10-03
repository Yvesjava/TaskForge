package cn.iocoder.yudao.module.agent.framework.git.platform;

/**
 * Git 平台 API 统一算子。
 *
 * <p>抽象 GitLab MR、GitHub PR 与 Gitea PR 的核心能力：创建合并请求、查询状态、
 * 执行合并。业务侧通过 {@link GitApiOperatorRegistry} 按平台路由，不感知平台差异。</p>
 *
 * @author TaskForge
 */
public interface GitApiOperator {

    /**
     * 本算子适配的平台。
     */
    GitPlatform platform();

    /**
     * 创建合并请求（GitLab MR / GitHub、Gitea PR）。
     */
    GitMergeRequestRef createMergeRequest(GitRepository repository, GitApiCredentials credentials, GitMergeRequest request);

    /**
     * 查询合并请求状态与可合并性。
     */
    GitMergeStatus queryMergeRequest(GitRepository repository, GitApiCredentials credentials, GitMergeRequestRef ref);

    /**
     * 执行合并。
     */
    GitMergeResult merge(GitRepository repository, GitApiCredentials credentials, GitMergeRequestRef ref, GitMergeOptions options);

}
