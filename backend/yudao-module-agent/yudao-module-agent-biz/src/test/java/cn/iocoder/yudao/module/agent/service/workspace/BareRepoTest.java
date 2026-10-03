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

/**
 * {@link BareRepoManager} 的单元测试类
 *
 * <p>使用真实 git 子进程与 {@code @TempDir} 验证裸仓库缓存
 * 的首次初始化、复用、拉取与并发锁定，避免破坏已有缓存。
 */
class BareRepoTest {

    @TempDir
    Path tempDir;

    private final GitCommandRunner gitRunner = new GitCommandRunner();

    @Test
    void ensureRepository_clonesBareRepositoryWhenMissing() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        BareRepoManager manager = manager(tempDir.resolve("cache"));

        Path repo = manager.ensureRepository("backend-service", source.toString());

        assertThat(repo).isEqualTo(manager.repositoryPath("backend-service"));
        assertThat(isBareRepository(repo)).isTrue();
    }

    @Test
    void ensureRepository_reusesExistingCacheWithoutReclone() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        BareRepoManager manager = manager(tempDir.resolve("cache"));
        Path repo = manager.ensureRepository("backend-service", source.toString());
        Files.writeString(repo.resolve("MARKER"), "keep-me");

        Path reused = manager.ensureRepository("backend-service", source.toString());

        assertThat(reused).isEqualTo(repo);
        assertThat(repo.resolve("MARKER")).exists();
        assertThat(isBareRepository(repo)).isTrue();
    }

    @Test
    void fetch_updatesBaselineBranch() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        BareRepoManager manager = manager(tempDir.resolve("cache"));
        Path repo = manager.ensureRepository("backend-service", source.toString());
        String before = revParse(repo, "refs/heads/main");

        Files.writeString(source.resolve("file.txt"), "v2\n");
        gitRunner.run(source, List.of("add", "."));
        gitRunner.run(source, List.of("commit", "-m", "second"));
        manager.fetch("backend-service", "main");

        String after = revParse(repo, "refs/heads/main");
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void fetch_rejectsInvalidBranch() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        BareRepoManager manager = manager(tempDir.resolve("cache"));
        manager.ensureRepository("backend-service", source.toString());

        assertThatThrownBy(() -> manager.fetch("backend-service", "bad branch"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.BARE_REPO_BRANCH_INVALID.getCode()));
    }

    @Test
    void prepare_initializesAndFetchesBaseline() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        BareRepoManager manager = manager(tempDir.resolve("cache"));

        Path repo = manager.prepare("backend-service", source.toString(), "main");

        assertThat(isBareRepository(repo)).isTrue();
        assertThat(revParse(repo, "refs/heads/main")).isNotBlank();
    }

    @Test
    void ensureRepository_rejectsInvalidProjectCode() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        BareRepoManager manager = manager(tempDir.resolve("cache"));

        assertThatThrownBy(() -> manager.ensureRepository("../escape", source.toString()))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.BARE_REPO_PROJECT_CODE_INVALID.getCode()));
    }

    @Test
    void ensureRepository_concurrentCalls_cloneOnlyOnce() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        BlockingCloneRunner runner = new BlockingCloneRunner();
        BareRepoManager manager = new BareRepoManager(properties(tempDir.resolve("cache")), runner);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Path> task = () -> {
                start.await();
                return manager.ensureRepository("backend-service", source.toString());
            };
            Future<Path> first = pool.submit(task);
            Future<Path> second = pool.submit(task);
            start.countDown();

            assertThat(runner.firstCloneEntered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(runner.secondCloneEntered.await(300, TimeUnit.MILLISECONDS)).isFalse();
            runner.releaseClone.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
            assertThat(runner.cloneCalls.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private BareRepoManager manager(Path cacheRoot) {
        return new BareRepoManager(properties(cacheRoot), gitRunner);
    }

    private AgentWorkspaceProperties properties(Path cacheRoot) {
        AgentWorkspaceProperties properties = new AgentWorkspaceProperties();
        properties.setBareRepoRoot(cacheRoot.toString());
        return properties;
    }

    private void initSourceRepo(Path source) throws Exception {
        gitRunner.run(tempDir, List.of("init", "-b", "main", source.toString()));
        gitRunner.run(source, List.of("config", "user.email", "test@example.com"));
        gitRunner.run(source, List.of("config", "user.name", "test"));
        Files.writeString(source.resolve("file.txt"), "v1\n");
        gitRunner.run(source, List.of("add", "."));
        gitRunner.run(source, List.of("commit", "-m", "init"));
    }

    private String revParse(Path repo, String ref) {
        return gitRunner.run(repo, List.of("rev-parse", ref)).trim();
    }

    private boolean isBareRepository(Path dir) {
        return Files.isDirectory(dir)
                && Files.isRegularFile(dir.resolve("HEAD"))
                && Files.isDirectory(dir.resolve("objects"))
                && Files.isDirectory(dir.resolve("refs"))
                && !Files.exists(dir.resolve(".git"));
    }

    /**
     * 阻塞 clone 调用以验证并发锁：两个并发请求只允许一次 clone。
     */
    private static final class BlockingCloneRunner extends GitCommandRunner {

        private final AtomicInteger cloneCalls = new AtomicInteger();
        private final CountDownLatch firstCloneEntered = new CountDownLatch(1);
        private final CountDownLatch secondCloneEntered = new CountDownLatch(1);
        private final CountDownLatch releaseClone = new CountDownLatch(1);

        @Override
        public String run(Path workingDirectory, List<String> args) {
            if (args.contains("clone")) {
                if (cloneCalls.incrementAndGet() >= 2) {
                    secondCloneEntered.countDown();
                }
                firstCloneEntered.countDown();
                try {
                    releaseClone.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.run(workingDirectory, args);
        }
    }

}
