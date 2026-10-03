package cn.iocoder.yudao.module.agent.service.workspace;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.agent.framework.workspace.config.AgentWorkspaceProperties;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandException;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandRunner;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitErrorNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link WorktreeManager} 清理流程的单元测试类
 *
 * <p>使用真实 git 子进程与 {@code @TempDir} 验证聚合工作区的幂等清理，
 * 以及清理失败时错误信息是否携带仓库与操作上下文。
 */
class WorktreeCleanupTest {

    @TempDir
    Path tempDir;

    private final GitCommandRunner gitRunner = new GitCommandRunner();

    @Test
    void destroyCompositeWorkspaceIfPresent_removesWorktreesAndRoot() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source, "file.txt", "v1\n");
        Path workspaceRoot = tempDir.resolve("workspace");
        Path bareRoot = tempDir.resolve("bare");
        WorktreeManager manager = manager(workspaceRoot, bareRoot);

        manager.createCompositeWorkspace(
                "TASK-1", "feat/x", "doc",
                List.of(WorkspaceProject.builder()
                        .projectCode("backend-service")
                        .gitUrl(source.toString())
                        .baseBranch("main")
                        .subDir("backend")
                        .build()));
        Path root = workspaceRoot.resolve("dirA-TASK-1");
        assertThat(root).exists();
        assertThat(root.resolve("backend/file.txt")).exists();

        manager.destroyCompositeWorkspaceIfPresent("TASK-1");

        assertThat(root).doesNotExist();
        assertThat(gitRunner.run(bareRoot.resolve("backend-service.git"), List.of("worktree", "list")))
                .doesNotContain("dirA-TASK-1");
    }

    @Test
    void destroyCompositeWorkspaceIfPresent_isIdempotent() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source, "file.txt", "v1\n");
        Path workspaceRoot = tempDir.resolve("workspace");
        Path bareRoot = tempDir.resolve("bare");
        WorktreeManager manager = manager(workspaceRoot, bareRoot);

        manager.createCompositeWorkspace(
                "TASK-1", "feat/x", "doc",
                List.of(WorkspaceProject.builder()
                        .projectCode("backend-service")
                        .gitUrl(source.toString())
                        .baseBranch("main")
                        .subDir("backend")
                        .build()));

        manager.destroyCompositeWorkspaceIfPresent("TASK-1");
        // 重复清理成功，不抛异常
        manager.destroyCompositeWorkspaceIfPresent("TASK-1");
        // 工作区从未创建时同样幂等返回
        manager.destroyCompositeWorkspaceIfPresent("TASK-MISSING");
    }

    @Test
    void destroyCompositeWorkspaceIfPresent_reportsRepositoryAndOperationContext() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source, "file.txt", "v1\n");
        Path workspaceRoot = tempDir.resolve("workspace");
        Path bareRoot = tempDir.resolve("bare");
        WorktreeManager manager = manager(workspaceRoot, bareRoot);

        manager.createCompositeWorkspace(
                "TASK-1", "feat/x", "doc",
                List.of(WorkspaceProject.builder()
                        .projectCode("backend-service")
                        .gitUrl(source.toString())
                        .baseBranch("main")
                        .subDir("backend")
                        .build()));

        WorktreeManager failingManager = manager(workspaceRoot, bareRoot, new FailingRemoveRunner());

        assertThatThrownBy(() -> failingManager.destroyCompositeWorkspaceIfPresent("TASK-1"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException serviceException = (ServiceException) ex;
                    assertThat(serviceException.getCode())
                            .isEqualTo(ErrorCodeConstants.WORKTREE_CLEANUP_FAILED.getCode());
                    assertThat(serviceException.getMessage())
                            .contains("backend-service")
                            .contains("worktree remove");
                });
    }

    @Test
    void gitErrorNormalizer_includesRepositoryAndOperationContext() {
        GitCommandException cause = new GitCommandException("git 失败", 128, "fatal: boom");

        String normalized = GitErrorNormalizer.normalize(cause, "backend-service", "worktree prune");

        assertThat(normalized)
                .contains("backend-service")
                .contains("worktree prune")
                .contains("git 失败");
    }

    private WorktreeManager manager(Path workspaceRoot, Path bareRoot) {
        return manager(workspaceRoot, bareRoot, gitRunner);
    }

    private WorktreeManager manager(Path workspaceRoot, Path bareRoot, GitCommandRunner runner) {
        AgentWorkspaceProperties properties = properties(workspaceRoot, bareRoot);
        return new WorktreeManager(properties, new BareRepoManager(properties, gitRunner), runner,
                new WorkflowInjector());
    }

    private AgentWorkspaceProperties properties(Path workspaceRoot, Path bareRoot) {
        AgentWorkspaceProperties properties = new AgentWorkspaceProperties();
        properties.setBareRepoRoot(bareRoot.toString());
        properties.setWorkspaceRoot(workspaceRoot.toString());
        return properties;
    }

    private void initSourceRepo(Path source, String fileName, String content) throws Exception {
        gitRunner.run(tempDir, List.of("init", "-b", "main", source.toString()));
        gitRunner.run(source, List.of("config", "user.email", "test@example.com"));
        gitRunner.run(source, List.of("config", "user.name", "test"));
        Files.writeString(source.resolve(fileName), content);
        gitRunner.run(source, List.of("add", "."));
        gitRunner.run(source, List.of("commit", "-m", "init"));
    }

    /**
     * 模拟 worktree remove 失败，验证清理错误携带仓库与操作上下文。
     */
    private static final class FailingRemoveRunner extends GitCommandRunner {

        @Override
        public String run(Path workingDirectory, List<String> args) {
            if (args.contains("remove")) {
                throw new GitCommandException("模拟清理失败", 1, "cleanup boom");
            }
            return super.run(workingDirectory, args);
        }
    }

}
