package cn.iocoder.yudao.module.agent.framework.secret;

import cn.iocoder.yudao.module.agent.framework.exec.CommandSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-OPS-01 凭证引用与日志脱敏测试
 *
 * <p>覆盖 URL、Header、命令参数与异常信息四类脱敏，以及凭证引用经 Secret Manager
 * 解析且绝不回显秘密的行为。</p>
 */
class SecretRedactTest {

    @Test
    void redactorMasksUrlCredentials() {
        assertThat(SecretRedactor.redact("https://oauth2:ghp_secret@github.com/org/repo.git"))
                .isEqualTo("https://***@github.com/org/repo.git");
        assertThat(SecretRedactor.redact("git@github.com:org/repo.git"))
                .isEqualTo("git@github.com:org/repo.git");
    }

    @Test
    void redactorMasksAuthorizationHeaders() {
        assertThat(SecretRedactor.redact("Authorization: Bearer supersecrettoken123"))
                .isEqualTo("Authorization: ***");
        assertThat(SecretRedactor.redact("Proxy-Authorization: Basic dXNlcjpwYXNz"))
                .isEqualTo("Proxy-Authorization: ***");
    }

    @Test
    void redactorMasksCommandArguments() {
        List<String> redacted = SecretRedactor.redactArguments(
                List.of("--token=abc123", "clone", "https://user:pwd@github.com/org/repo.git"), List.of());

        assertThat(redacted).containsExactly("--token=***", "clone", "https://***@github.com/org/repo.git");

        CommandSpec command = new CommandSpec("git", List.of("clone", "https://user:pwd@github.com/org/repo.git"), null);
        assertThat(command.display()).isEqualTo("git clone https://***@github.com/org/repo.git");
    }

    @Test
    void redactorMasksExceptionMessages() {
        assertThat(SecretRedactor.redact("authentication failed: token=abc123 for https://u:p@host/repo.git"))
                .isEqualTo("authentication failed: token=*** for https://***@host/repo.git");
        assertThat(SecretRedactor.describe(new RuntimeException("password=hunter2 leaked"), List.of()))
                .isEqualTo("password=*** leaked");
        assertThat(SecretRedactor.describe(new IllegalStateException("unknown error"), List.of()))
                .isEqualTo("unknown error");
    }

    @Test
    void knownSecretValueIsMaskedBeforePatternRules() {
        String secret = "ghp_ABCDEFGHIJKLMNOPQRST";
        String input = "using credential " + secret + " and also token=" + secret;

        String redacted = SecretRedactor.redact(input, List.of(secret));

        assertThat(redacted).doesNotContain(secret).contains("***");
    }

    @Test
    void secretManagerResolvesConfiguredReferenceOnly() {
        AgentSecretProperties properties = new AgentSecretProperties();
        properties.setValues(Map.of("taskforge-git", "ghp_abcdef"));
        PropertySecretManager manager = new PropertySecretManager(properties);

        assertThat(manager.resolve("taskforge-git")).contains("ghp_abcdef");
        assertThat(manager.resolve("  taskforge-git  ")).contains("ghp_abcdef");
        assertThat(manager.resolve("missing-ref")).isEmpty();
        assertThat(manager.resolve(null)).isEmpty();
        assertThat(manager.resolve(" ")).isEmpty();
    }

    @Test
    void envKeyNormalizesReferenceForEnvironmentFallback() {
        assertThat(PropertySecretManager.envKey("taskforge-git"))
                .isEqualTo("TASKFORGE_SECRET_TASKFORGE_GIT");
        assertThat(PropertySecretManager.envKey("github.com/org"))
                .isEqualTo("TASKFORGE_SECRET_GITHUB_COM_ORG");
    }

    @Test
    void gitCredentialResolverUsesReferenceAndNeverLeaksSecret() {
        AgentSecretProperties properties = new AgentSecretProperties();
        properties.setValues(Map.of("taskforge-git", "ghp_abcdef"));
        GitCredentialResolver resolver = new GitCredentialResolver(new PropertySecretManager(properties));

        assertThat(resolver.resolve("taskforge-git").token()).isEqualTo("ghp_abcdef");

        assertThatThrownBy(() -> resolver.resolve("unknown-ref"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown-ref")
                .hasMessageNotContaining("ghp_abcdef");

        assertThatThrownBy(() -> resolver.resolve(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("credential_ref");
    }

}
