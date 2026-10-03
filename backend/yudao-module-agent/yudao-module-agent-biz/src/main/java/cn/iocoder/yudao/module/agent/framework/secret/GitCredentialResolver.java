package cn.iocoder.yudao.module.agent.framework.secret;

import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiCredentials;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Git 凭证解析器
 *
 * <p>把 {@code agent_project.credential_ref} 转换为平台访问凭证。凭证值由
 * {@link SecretManager} 按引用读取，本类只负责校验引用并组装对象，绝不落盘或
 * 返回原始秘密。解析失败时错误信息只包含引用本身，便于排障且不泄露秘密。</p>
 *
 * @author TaskForge
 */
@Component
public class GitCredentialResolver {

    private final SecretManager secretManager;

    public GitCredentialResolver(SecretManager secretManager) {
        this.secretManager = Objects.requireNonNull(secretManager, "secretManager 不能为空");
    }

    public GitApiCredentials resolve(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            throw new IllegalStateException("项目未配置 credential_ref，无法解析 Git 凭证");
        }
        String reference = credentialRef.trim();
        String token = secretManager.resolve(reference)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "无法从 Secret Manager 解析凭证引用：" + reference));
        return GitApiCredentials.of(token);
    }

}
