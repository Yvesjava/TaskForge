package cn.iocoder.yudao.module.agent.framework.git.platform;

import java.util.Objects;

/**
 * Git 平台 API 访问凭证。
 *
 * @param token 访问令牌（GitLab Private Token / GitHub、Gitea Personal Access Token）
 * @author TaskForge
 */
public record GitApiCredentials(String token) {

    public GitApiCredentials {
        Objects.requireNonNull(token, "token 不能为空");
        if (token.isBlank()) {
            throw new IllegalArgumentException("token 不能为空");
        }
    }

    public static GitApiCredentials of(String token) {
        return new GitApiCredentials(token);
    }

}
