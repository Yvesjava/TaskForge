package cn.iocoder.yudao.module.agent.framework.git.platform;

/**
 * Git 代码托管平台枚举。
 *
 * <p>用于路由统一 Git API 算子。新增平台时需同时提供 {@link GitApiOperator} 实现，
 * 并交由 {@link GitApiOperatorRegistry} 注册。</p>
 *
 * @author TaskForge
 */
public enum GitPlatform {

    GITLAB("GitLab"),
    GITHUB("GitHub"),
    GITEA("Gitea");

    private final String label;

    GitPlatform(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
