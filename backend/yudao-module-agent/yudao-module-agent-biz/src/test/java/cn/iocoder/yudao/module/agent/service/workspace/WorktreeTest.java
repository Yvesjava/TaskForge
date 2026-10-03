package cn.iocoder.yudao.module.agent.service.workspace;

import cn.iocoder.yudao.module.agent.framework.workspace.config.AgentWorkspaceProperties;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-QA-03 Worktree 生命周期测试（真实 git 子进程）。
 *
 * <p>覆盖多仓聚合工作区的完整生命周期：创建并挂载多个项目、写入 {@code .ai}
 * 执行约束、检出目标特性分支、扫描残留目录、幂等销毁以及工作区根目录不存在
 * 时的兜底行为。全部场景通过 {@code @TempDir} 与真实 {@code git} 子进程完成，
 * 不依赖任何外部服务。</p>
 */
class WorktreeTest {

    @TempDir
    Path tempDir;

    private final GitCommandRunner gitRunner = new GitCommandRunner();

    @Test
    void fullLifecycle_createsMountsArtifactsDetectsResidueAndDestroys() throws Exception {
        Path backend = initRepo("backend-src", "pom.xml", "<project/>\n");
        Path frontend = initRepo("frontend-src", "package.json", "{}\n");
        Path workspaceRoot = tempDir.resolve("workspace");
        Path bareRoot = tempDir.resolve("bare");
        WorktreeManager manager = manager(workspaceRoot, bareRoot);

        CompositeWorkspace workspace = manager.createCompositeWorkspace(
                "TASK-QA-03-001", "feature/TASK-QA-03-001", "# e2e worktree",
                List.of(
                        WorkspaceProject.builder()
                                .projectCode("backend-service")
                                .gitUrl(backend.toString())
                                .baseBranch("main")
                                .subDir("backend")
                                .build(),
                        WorkspaceProject.builder()
                                .projectCode("frontend-portal")
                                .gitUrl(frontend.toString())
                                .baseBranch("main")
                                .subDir("frontend")
                                .build()));

        assertThat(workspace.getRoot().resolve(".ai/task.md")).hasContent("# e2e worktree");
        assertThat(workspace.getRoot().resolve(".ai/WORKFLOW.md")).exists();
        assertThat(branchOf(workspace.getRoot().resolve("backend"))).isEqualTo("feature/TASK-QA-03-001");
        assertThat(branchOf(workspace.getRoot().resolve("frontend"))).isEqualTo("feature/TASK-QA-03-001");
        assertThat(workspace.getRoot().resolve("backend/pom.xml")).exists();
        assertThat(workspace.getRoot().resolve("frontend/package.json")).exists();
        assertThat(manager.detectResidue()).contains(workspace.getRoot());

        manager.destroyCompositeWorkspaceIfPresent("TASK-QA-03-001");

        assertThat(workspace.getRoot()).doesNotExist();
        assertThat(manager.detectResidue()).isEmpty();
        assertThat(gitRunner.run(bareRoot.resolve("backend-service.git"), List.of("worktree", "list")))
                .doesNotContain("dirA-TASK-QA-03-001");
        assertThat(gitRunner.run(bareRoot.resolve("frontend-portal.git"), List.of("worktree", "list")))
                .doesNotContain("dirA-TASK-QA-03-001");
    }

    @Test
    void destroy_isIdempotentAcrossCallsAndMissingTask() throws Exception {
        Path source = initRepo("source", "file.txt", "v1\n");
        WorktreeManager manager = manager(tempDir.resolve("workspace"), tempDir.resolve("bare"));
        manager.createCompositeWorkspace(
                "TASK-QA-03-002", "feature/x", "doc",
                List.of(WorkspaceProject.builder()
                        .projectCode("backend-service")
                        .gitUrl(source.toString())
                        .baseBranch("main")
                        .subDir("backend")
                        .build()));
        Path root = tempDir.resolve("workspace").resolve("dirA-TASK-QA-03-002");
        assertThat(root).exists();

        manager.destroyCompositeWorkspaceIfPresent("TASK-QA-03-002");
        manager.destroyCompositeWorkspaceIfPresent("TASK-QA-03-002");
        manager.destroyCompositeWorkspaceIfPresent("TASK-QA-03-MISSING");

        assertThat(root).doesNotExist();
        assertThat(manager.detectResidue()).isEmpty();
    }

    @Test
    void detectResidue_returnsEmptyWhenWorkspaceRootDoesNotExist() {
        WorktreeManager manager = manager(tempDir.resolve("workspace-missing"), tempDir.resolve("bare"));

        assertThat(manager.detectResidue()).isEmpty();
    }

    private WorktreeManager manager(Path workspaceRoot, Path bareRoot) {
        AgentWorkspaceProperties properties = new AgentWorkspaceProperties();
        properties.setBareRepoRoot(bareRoot.toString());
        properties.setWorkspaceRoot(workspaceRoot.toString());
        return new WorktreeManager(properties, new BareRepoManager(properties, gitRunner), gitRunner,
                new WorkflowInjector());
    }

    private Path initRepo(String name, String fileName, String content) throws Exception {
        Path source = tempDir.resolve(name);
        gitRunner.run(tempDir, List.of("init", "-b", "main", source.toString()));
        gitRunner.run(source, List.of("config", "user.email", "test@example.com"));
        gitRunner.run(source, List.of("config", "user.name", "test"));
        Files.writeString(source.resolve(fileName), content);
        gitRunner.run(source, List.of("add", "."));
        gitRunner.run(source, List.of("commit", "-m", "init"));
        return source;
    }

    private String branchOf(Path worktree) {
        return gitRunner.run(worktree, List.of("symbolic-ref", "--short", "HEAD")).trim();
    }

}
