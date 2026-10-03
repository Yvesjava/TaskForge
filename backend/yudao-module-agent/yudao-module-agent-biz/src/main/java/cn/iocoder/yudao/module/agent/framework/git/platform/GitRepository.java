package cn.iocoder.yudao.module.agent.framework.git.platform;

import java.util.Locale;

/**
 * Git 平台仓库坐标。
 *
 * <p>统一描述某个平台上的 owner/namespace 与仓库名，以及该平台的 REST API 基地址，
 * 避免算子实现自行拼接、解析仓库地址。</p>
 *
 * @param platform Git 平台
 * @param host     仓库托管主机名（小写）
 * @param owner    仓库 owner/namespace（GitLab 子组使用 {@code /} 分隔）
 * @param name     仓库名（不含 {@code .git}）
 * @param baseUrl  REST API 基地址；为空时按平台与主机推导
 * @author TaskForge
 */
public record GitRepository(GitPlatform platform, String host, String owner, String name, String baseUrl) {

    public GitRepository {
        if (platform == null) {
            throw new IllegalArgumentException("platform 不能为空");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host 不能为空");
        }
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        host = host.toLowerCase(Locale.ROOT);
        baseUrl = baseUrl == null || baseUrl.isBlank() ? defaultBaseUrl(platform, host) : baseUrl;
    }

    /**
     * GitLab 项目路径 / GitHub、Gitea 仓库全名（{@code owner/name}）。
     */
    public String path() {
        return owner + "/" + name;
    }

    public static GitRepository of(GitPlatform platform, String host, String owner, String name) {
        return new GitRepository(platform, host, owner, name, null);
    }

    public static GitRepository of(GitPlatform platform, String host, String owner, String name, String baseUrl) {
        return new GitRepository(platform, host, owner, name, baseUrl);
    }

    static String defaultBaseUrl(GitPlatform platform, String host) {
        return switch (platform) {
            case GITHUB -> "github.com".equals(host)
                    ? "https://api.github.com" : "https://" + host + "/api/v3";
            case GITLAB -> "https://" + host + "/api/v4";
            case GITEA -> "https://" + host + "/api/v1";
        };
    }

}
