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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 工作区路径安全、并发互斥与残留检测测试。
 *
 * <p>使用真实 git 子进程与 {@code @TempDir} 验证路径逃逸拒绝、
 * 符号链接拒绝、同任务并发互斥，以及异常退出后的残留目录发现。
 */
class PathSafetyTest {

    @TempDir
    Path tempDir;

    private final GitCommandRunner gitRunner = new GitCommandRunner();

    @Test
    void createCompositeWorkspace_rejectsAbsoluteSubDir() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source, "file.txt", "v1\n");
        WorktreeManager manager = manager(tempDir.resolve("workspace"), tempDir.resolve("bare"));

        assertThatThrownBy(() -> manager.createCompositeWorkspace(
                "TASK-ABS-1", "feat/x", "doc",
                List.of(WorkspaceProject.builder().projectCode("backend-service")
                        .gitUrl(source.toString()).baseBranch("main").subDir("/absolute").build())))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.WORKTREE_SUB_DIR_INVALID.getCode()));
    }

    @Test
    void createCompositeWorkspace_rejectsTraversalSubDir() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source, "file.txt", "v1\n");
        WorktreeManager manager = manager(tempDir.resolve("workspace"), tempDir.resolve("bare"));

        assertThatThrownBy(() -> manager.createCompositeWorkspace(
                "TASK-DOTDOT-1", "feat/x", "doc",
                List.of(WorkspaceProject.builder().projectCode("backend-service")
                        .gitUrl(source.toString()).baseBranch("main").subDir("../escape").build())))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.WORKTREE_SUB_DIR_INVALID.getCode()));
    }

    @Test
    void createCompositeWorkspace_rejectsBackslashSubDir() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source, "file.txt", "v1\n");
        WorktreeManager manager = manager(tempDir.resolve("workspace"), tempDir.resolve("bare"));

        assertThatThrownBy(() -> manager.createCompositeWorkspace(
                "TASK-BACKSLASH-1", "feat/x", "doc",
                List.of(WorkspaceProject.builder().projectCode("backend-service")
                        .gitUrl(source.toString()).baseBranch("main").subDir("..\\escape").build())))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.WORKTREE_SUB_DIR_INVALID.getCode()));
    }

    @Test
    void createCompositeWorkspace_rejectsEscapingTaskNo() {
        WorktreeManager manager = manager(tempDir.resolve("workspace"), tempDir.resolve("bare"));

        assertThatThrownBy(() -> manager.createCompositeWorkspace(
                "../escape", "feat/x", "doc", List.of()))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.WORKTREE_TASK_NO_INVALID.getCode()));
    }

    @Test
    void createCompositeWorkspace_rejectsSymlinkedAggregateRoot() throws Exception {
        assumeTrue(symlinksSupported(), "符号链接在当前环境不可用");
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace"));
        Files.createSymbolicLink(workspaceRoot.resolve("dirA-TASK-1"), outside);

        WorktreeManager manager = manager(workspaceRoot, tempDir.resolve("bare"));

        assertThatThrownBy(() -> manager.createCompositeWorkspace(
                "TASK-1", "feat/x", "doc",
                List.of(WorkspaceProject.builder().projectCode("backend-service")
                        .gitUrl("git@host:repo.git").baseBranch("main").subDir("backend").build())))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.WORKTREE_SYMLINK_NOT_ALLOWED.getCode()));
    }

    @Test
    void createCompositeWorkspace_rejectsSymlinkedSubDir() throws Exception {
        assumeTrue(symlinksSupported(), "符号链接在当前环境不可用");
        Path source = tempDir.resolve("source");
        initSourceRepo(source, "file.txt", "v1\n");
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace"));
        Path root = Files.createDirectories(workspaceRoot.resolve("dirA-TASK-1"));
        Files.createSymbolicLink(root.resolve("evil"), outside);

        WorktreeManager manager = manager(workspaceRoot, tempDir.resolve("bare"));

        assertThatThrownBy(() -> manager.createCompositeWorkspace(
                "TASK-1", "feat/x", "doc",
                List.of(WorkspaceProject.builder().projectCode("backend-service")
                        .gitUrl(source.toString()).baseBranch("main").subDir("evil").build())))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.WORKTREE_SYMLINK_NOT_ALLOWED.getCode()));
    }

    @Test
    void createCompositeWorkspace_serializesConcurrentCreationForSameTask() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source, "file.txt", "v1\n");
        BlockingWorktreeRunner runner = new BlockingWorktreeRunner();
        AgentWorkspaceProperties properties = properties(tempDir.resolve("workspace"), tempDir.resolve("bare"));
        WorktreeManager manager = new WorktreeManager(properties, new BareRepoManager(properties, runner), runner);

        List<WorkspaceProject> projects = List.of(
                WorkspaceProject.builder().projectCode("backend-service")
                        .gitUrl(source.toString()).baseBranch("main").subDir("backend").build());
        String taskNo = "TASK-MUTEX-1";
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> task = () -> {
                start.await();
                try {
                    manager.createCompositeWorkspace(taskNo, "feat/x", "doc", projects);
                    success.incrementAndGet();
                } catch (RuntimeException e) {
                    failed.incrementAndGet();
                }
                return null;
            };
            Future<Void> first = pool.submit(task);
            Future<Void> second = pool.submit(task);
            start.countDown();

            assertThat(runner.firstWorktreeEntered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(runner.secondWorktreeEntered.await(300, TimeUnit.MILLISECONDS)).isFalse();
            runner.releaseWorktree.countDown();

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(success.get()).isEqualTo(1);
        assertThat(failed.get()).isEqualTo(1);
        assertThat(runner.worktreeAddCalls.get()).isEqualTo(1);
    }

    @Test
    void detectResidue_returnsEmptyWhenWorkspaceRootMissing() {
        WorktreeManager manager = manager(tempDir.resolve("workspace"), tempDir.resolve("bare"));

        assertThat(manager.detectResidue()).isEmpty();
    }

    @Test
    void detectResidue_discoversLeftoverAggregateDirectories() throws Exception {
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace"));
        Path residue = Files.createDirectories(workspaceRoot.resolve("dirA-LEFTOVER"));
        Files.writeString(residue.resolve("stale.txt"), "leftover");
        Files.createDirectories(workspaceRoot.resolve("unrelated"));
        Path other = Files.createDirectories(workspaceRoot.resolve("dirA-OTHER"));

        WorktreeManager manager = manager(workspaceRoot, tempDir.resolve("bare"));

        assertThat(manager.detectResidue())
                .containsExactlyInAnyOrder(
                        residue.toAbsolutePath().normalize(),
                        other.toAbsolutePath().normalize());
    }

    private WorktreeManager manager(Path workspaceRoot, Path bareRoot) {
        AgentWorkspaceProperties properties = properties(workspaceRoot, bareRoot);
        return new WorktreeManager(properties, new BareRepoManager(properties, gitRunner), gitRunner);
    }

    private AgentWorkspaceProperties properties(Path workspaceRoot, Path bareRoot) {
        AgentWorkspaceProperties properties = new AgentWorkspaceProperties();
        properties.setWorkspaceRoot(workspaceRoot.toString());
        properties.setBareRepoRoot(bareRoot.toString());
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

    private boolean symlinksSupported() {
        try {
            Path target = Files.createFile(tempDir.resolve("symlink-target"));
            Path link = tempDir.resolve("symlink-link");
            Files.createSymbolicLink(link, target);
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 阻塞首个 {@code git worktree add} 以验证同任务并发创建互斥。
     */
    private static final class BlockingWorktreeRunner extends GitCommandRunner {

        private final AtomicInteger worktreeAddCalls = new AtomicInteger();
        private final CountDownLatch firstWorktreeEntered = new CountDownLatch(1);
        private final CountDownLatch secondWorktreeEntered = new CountDownLatch(1);
        private final CountDownLatch releaseWorktree = new CountDownLatch(1);

        @Override
        public String run(Path workingDirectory, List<String> args) {
            if (args.contains("worktree") && args.contains("add")) {
                if (worktreeAddCalls.incrementAndGet() >= 2) {
                    secondWorktreeEntered.countDown();
                }
                firstWorktreeEntered.countDown();
                try {
                    releaseWorktree.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.run(workingDirectory, args);
        }
    }

}
