package cn.iocoder.yudao.module.agent.service.merge;

import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiCredentials;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeRequest;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeRequestRef;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitRepository;

import java.util.Objects;

/**
 * 单个仓库的合并预检目标。
 *
 * <p>既支持“创建 MR/PR 后预检”（{@code ref} 为空时按 {@code request} 创建），
 * 也支持“对已存在 MR/PR 预检”（直接使用 {@code ref} 查询）。</p>
 *
 * @param repoKey     仓库标识（如项目代号），用于结果回填与排障
 * @param repository  Git 平台仓库坐标
 * @param credentials 平台访问凭证
 * @param request     创建 MR/PR 所需输入；{@code ref} 为空时必填
 * @param ref         已存在的 MR/PR 引用；为空时按 {@code request} 创建
 * @author TaskForge
 */
public record MergePrecheckTarget(String repoKey, GitRepository repository, GitApiCredentials credentials,
                                  GitMergeRequest request, GitMergeRequestRef ref) {

    public MergePrecheckTarget {
        if (repoKey == null || repoKey.isBlank()) {
            throw new IllegalArgumentException("repoKey 不能为空");
        }
        Objects.requireNonNull(repository, "repository 不能为空");
        Objects.requireNonNull(credentials, "credentials 不能为空");
        if (request == null && ref == null) {
            throw new IllegalArgumentException("request 与 ref 不能同时为空");
        }
    }

    public static MergePrecheckTarget create(String repoKey, GitRepository repository,
                                             GitApiCredentials credentials, GitMergeRequest request) {
        return new MergePrecheckTarget(repoKey, repository, credentials, request, null);
    }

    public static MergePrecheckTarget query(String repoKey, GitRepository repository,
                                            GitApiCredentials credentials, GitMergeRequestRef ref) {
        return new MergePrecheckTarget(repoKey, repository, credentials, null, ref);
    }

}
