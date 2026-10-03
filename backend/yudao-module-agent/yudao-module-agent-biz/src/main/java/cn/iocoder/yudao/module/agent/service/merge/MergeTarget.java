package cn.iocoder.yudao.module.agent.service.merge;

import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiCredentials;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeOptions;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeRequestRef;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitRepository;

import java.util.Objects;

/**
 * 单个仓库的合并目标。
 *
 * <p>合并编排复用预检阶段创建的 MR/PR 引用，按平台算子执行合并动作。每个目标
 * 携带独立的合并策略（Fast-Forward / Squash），以满足不同仓库的合并偏好。</p>
 *
 * @param repoKey     仓库标识（项目代号），用于结果回填与排障
 * @param repository  Git 平台仓库坐标
 * @param credentials 平台访问凭证
 * @param ref         待合并的 MR/PR 引用
 * @param options     合并选项（Fast-Forward / Squash 等）
 * @author TaskForge
 */
public record MergeTarget(String repoKey, GitRepository repository, GitApiCredentials credentials,
                          GitMergeRequestRef ref, GitMergeOptions options) {

    public MergeTarget {
        if (repoKey == null || repoKey.isBlank()) {
            throw new IllegalArgumentException("repoKey 不能为空");
        }
        Objects.requireNonNull(repository, "repository 不能为空");
        Objects.requireNonNull(credentials, "credentials 不能为空");
        Objects.requireNonNull(ref, "ref 不能为空");
        Objects.requireNonNull(options, "options 不能为空");
    }

    public static MergeTarget of(String repoKey, GitRepository repository,
                                 GitApiCredentials credentials, GitMergeRequestRef ref,
                                 GitMergeOptions options) {
        return new MergeTarget(repoKey, repository, credentials, ref, options);
    }

}
