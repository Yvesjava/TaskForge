package cn.iocoder.yudao.module.agent.service.workspace;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.agent.framework.workspace.config.AgentWorkspaceProperties;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link WorktreeManager} 的单元测试类
 *
 * <p>使用真实 git 子进程与 {@code @TempDir} 验证多仓聚合挂载、
 * 任务文档写入、子目录安全校验以及失败回滚，避免破坏真实工作区。
 */
class WorktreeManagerTest {

    @TempDir
    Path tempDir;

    private final GitCommandRunner gitRunner = new GitCommandRunner();

    @Test
    void createCompositeWorkspace_mountsProjectsBySubDirMapping() throws Exception {
        Path backend = tempDir.resolve("backend-src");
        Path frontend = tempDir.resolve("frontend-src");
        initSourceRepo(backend, "pom.xml", "<project/>\n");
        initSourceRepo(frontend, "package.json", "{}\n");
        WorktreeManager manager = manager(tempDir.resolve("workspace"), tempDir.resolve("bare"));

        CompositeWorkspace workspace = manager.createCompositeWorkspace(
                "TASK-20261003-001", "feat/lzc-34", "# Task\n\nbuild backend and frontend",
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

        assertThat(workspace.getRoot()).isEqualTo(
                tempDir.resolve("workspace").resolve("dirA-TASK-20261003-001").toAbsolutePath().normalize());
        assertThat(workspace.getRoot().resolve("backend/pom.xml")).exists();
        assertThat(workspace.getRoot().resolve("frontend/package.json")).exists();
        assertThat(workspace.getRoot().resolve(".ai/task.md")).hasContent("# Task\n\nbuild backend and frontend");
        assertThat(workspace.getProjects()).extracting(MountedProject::getSubDir)
                .containsExactly("backend", "frontend");
        assertThat(branchOf(workspace.getRoot().resolve("backend"))).isEqualTo("feat/lzc-34");
        assertThat(branchOf(workspace.getRoot().resolve("frontend"))).isEqualTo("feat/lzc-34");
    }

    @Test
    void createCompositeWorkspace_rejectsEscapingSubDir() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source, "file.txt", "v1\n");
        WorktreeManager manager = manager(tempDir.resolve("workspace"), tempDir.resolve("bare"));

        assertThatThrownBy(() -> manager.createCompositeWorkspace(
                "TASK-1", "feat/x", "doc",
                List.of(WorkspaceProject.builder()
                        .projectCode("backend-service")
                        .gitUrl(source.toString())
                        .baseBranch("main")
                        .subDir("../escape")
                        .build())))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.WORKTREE_SUB_DIR_INVALID.getCode()));
    }

    @Test
    void createCompositeWorkspace_rejectsDuplicateSubDir() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source, "file.txt", "v1\n");
        WorktreeManager manager = manager(tempDir.resolve("workspace"), tempDir.resolve("bare"));

        assertThatThrownBy(() -> manager.createCompositeWorkspace(
                "TASK-1", "feat/x", "doc",
                List.of(
                        WorkspaceProject.builder().projectCode("backend-service").gitUrl(source.toString())
                                .baseBranch("main").subDir("backend").build(),
                        WorkspaceProject.builder().projectCode("frontend-portal").gitUrl(source.toString())
                                .baseBranch("main").subDir("backend").build())))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.WORKTREE_SUB_DIR_CONFLICT.getCode()));
    }

    @Test
    void createCompositeWorkspace_rollsBackMountedProjectsOnFailure() throws Exception {
        Path backend = tempDir.resolve("backend-src");
        initSourceRepo(backend, "pom.xml", "<project/>\n");
        WorktreeManager manager = manager(tempDir.resolve("workspace"), tempDir.resolve("bare"));
        Path root = tempDir.resolve("workspace").resolve("dirA-TASK-1");

        assertThatThrownBy(() -> manager.createCompositeWorkspace(
                "TASK-1", "feat/x", "doc",
                List.of(
                        WorkspaceProject.builder().projectCode("backend-service").gitUrl(backend.toString())
                                .baseBranch("main").subDir("backend").build(),
                        WorkspaceProject.builder().projectCode("missing-service")
                                .gitUrl(tempDir.resolve("does-not-exist").toString())
                                .baseBranch("main").subDir("frontend").build())))
                .isInstanceOf(ServiceException.class);

        assertThat(root.resolve("backend")).doesNotExist();
        assertThat(root).doesNotExist();
    }

    private WorktreeManager manager(Path workspaceRoot, Path bareRoot) {
        AgentWorkspaceProperties properties = new AgentWorkspaceProperties();
        properties.setBareRepoRoot(bareRoot.toString());
        properties.setWorkspaceRoot(workspaceRoot.toString());
        return new WorktreeManager(properties, new BareRepoManager(properties, gitRunner), gitRunner,
                new WorkflowInjector());
    }

    private void initSourceRepo(Path source, String fileName, String content) throws Exception {
        gitRunner.run(tempDir, List.of("init", "-b", "main", source.toString()));
        gitRunner.run(source, List.of("config", "user.email", "test@example.com"));
        gitRunner.run(source, List.of("config", "user.name", "test"));
        Files.writeString(source.resolve(fileName), content);
        gitRunner.run(source, List.of("add", "."));
        gitRunner.run(source, List.of("commit", "-m", "init"));
    }

    private String branchOf(Path worktree) {
        return gitRunner.run(worktree, List.of("symbolic-ref", "--short", "HEAD")).trim();
    }

}
