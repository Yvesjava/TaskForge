package cn.iocoder.yudao.module.agent.framework.git.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Git 仓库地址解析器测试。
 */
class GitRepositoryParserTest {

    @Test
    void parse_httpsGithub() {
        GitRepository repository = GitRepositoryParser.parse("https://github.com/Yvesjava/TaskForge.git");

        assertThat(repository.platform()).isEqualTo(GitPlatform.GITHUB);
        assertThat(repository.host()).isEqualTo("github.com");
        assertThat(repository.owner()).isEqualTo("Yvesjava");
        assertThat(repository.name()).isEqualTo("TaskForge");
        assertThat(repository.baseUrl()).isEqualTo("https://api.github.com");
    }

    @Test
    void parse_scpGitLabSubgroup() {
        GitRepository repository = GitRepositoryParser.parse("git@gitlab.com:group/sub/demo.git");

        assertThat(repository.platform()).isEqualTo(GitPlatform.GITLAB);
        assertThat(repository.owner()).isEqualTo("group/sub");
        assertThat(repository.name()).isEqualTo("demo");
        assertThat(repository.path()).isEqualTo("group/sub/demo");
        assertThat(repository.baseUrl()).isEqualTo("https://gitlab.com/api/v4");
    }

    @Test
    void parse_httpsGitea() {
        GitRepository repository = GitRepositoryParser.parse("https://gitea.com/org/demo");

        assertThat(repository.platform()).isEqualTo(GitPlatform.GITEA);
        assertThat(repository.baseUrl()).isEqualTo("https://gitea.com/api/v1");
    }

    @Test
    void parse_explicitPlatformForSelfHosted() {
        GitRepository repository = GitRepositoryParser.parse(
                "https://git.example.com/team/demo.git", GitPlatform.GITLAB);

        assertThat(repository.platform()).isEqualTo(GitPlatform.GITLAB);
        assertThat(repository.baseUrl()).isEqualTo("https://git.example.com/api/v4");
    }

    @Test
    void parse_unrecognizedHost_throws() {
        assertThatThrownBy(() -> GitRepositoryParser.parse("https://git.example.com/team/demo.git"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法识别 Git 平台");
    }

    @Test
    void parse_missingRepo_throws() {
        assertThatThrownBy(() -> GitRepositoryParser.parse("https://github.com/only-owner"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner/repo");
    }

}
