package cn.iocoder.yudao.module.agent.service.workspace;

import cn.iocoder.yudao.module.agent.framework.workspace.config.AgentWorkspaceProperties;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandException;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandRunner;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitErrorNormalizer;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitRefs;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.BARE_REPO_BRANCH_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.BARE_REPO_FETCH_FAILED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.BARE_REPO_INIT_FAILED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.BARE_REPO_PROJECT_CODE_INVALID;

/**
 * Bare Repo 缓存管理器
 *
 * <p>在宿主机维护项目裸仓库缓存：首次使用时执行一次
 * {@code git clone --bare}，后续复用已有缓存并执行
 * {@code git fetch} 更新基线，避免重复克隆破坏缓存。
 *
 * <p>同一项目代号在 JVM 内通过 {@link ReentrantLock} 串行化，
 * 防止并发 init/fetch 互相覆盖。
 *
 * @author TaskForge
 */
@Component
public class BareRepoManager {

    /**
     * 项目代号允许的目录名模式：字母数字开头，只含字母、数字、点、下划线、连字符
     */
    private static final String PROJECT_CODE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]*";

    private final Path bareRepoRoot;

    private final GitCommandRunner gitRunner;

    /**
     * 按项目代号隔离的锁
     */
    private final ConcurrentMap<String, ReentrantLock> projectLocks = new ConcurrentHashMap<>();

    public BareRepoManager(AgentWorkspaceProperties properties, GitCommandRunner gitRunner) {
        this.bareRepoRoot = Paths.get(properties.getBareRepoRoot()).toAbsolutePath().normalize();
        this.gitRunner = gitRunner;
    }

    /**
     * 确保指定项目的裸仓库缓存存在，不存在则执行一次 clone。
     *
     * @param projectCode 项目代号
     * @param gitUrl      仓库地址
     * @return 裸仓库目录
     */
    public Path ensureRepository(String projectCode, String gitUrl) {
        validateProjectCode(projectCode);
        ReentrantLock lock = lockFor(projectCode);
        lock.lock();
        try {
            return ensureRepositoryLocked(projectCode, gitUrl);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 拉取指定分支最新基线。
     *
     * @param projectCode 项目代号
     * @param branch      分支名
     */
    public void fetch(String projectCode, String branch) {
        validateProjectCode(projectCode);
        validateBranch(projectCode, branch);
        ReentrantLock lock = lockFor(projectCode);
        lock.lock();
        try {
            fetchLocked(projectCode, branch);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 原子执行初始化 + 拉取，返回就绪的裸仓库目录。
     *
     * @param projectCode 项目代号
     * @param gitUrl      仓库地址
     * @param branch      基线分支
     * @return 裸仓库目录
     */
    public Path prepare(String projectCode, String gitUrl, String branch) {
        validateProjectCode(projectCode);
        validateBranch(projectCode, branch);
        ReentrantLock lock = lockFor(projectCode);
        lock.lock();
        try {
            Path repo = ensureRepositoryLocked(projectCode, gitUrl);
            fetchLocked(projectCode, branch);
            return repo;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取项目裸仓库缓存路径（不创建）。
     */
    public Path repositoryPath(String projectCode) {
        validateProjectCode(projectCode);
        return bareRepoRoot.resolve(projectCode + ".git");
    }

    private Path ensureRepositoryLocked(String projectCode, String gitUrl) {
        Path repoDir = repositoryPath(projectCode);
        if (isBareRepository(repoDir)) {
            return repoDir;
        }
        try {
            Files.createDirectories(bareRepoRoot);
            gitRunner.run(bareRepoRoot, List.of("clone", "--bare", gitUrl, repoDir.toString()));
        } catch (GitCommandException | IOException e) {
            throw exception(BARE_REPO_INIT_FAILED, projectCode, GitErrorNormalizer.detail(e));
        }
        if (!isBareRepository(repoDir)) {
            throw exception(BARE_REPO_INIT_FAILED, projectCode, "clone 后未生成裸仓库目录");
        }
        return repoDir;
    }

    private void fetchLocked(String projectCode, String branch) {
        Path repoDir = repositoryPath(projectCode);
        if (!isBareRepository(repoDir)) {
            throw exception(BARE_REPO_FETCH_FAILED, projectCode, branch, "裸仓库缓存未初始化");
        }
        try {
            // 显式 refspec 更新本地 heads，避免依赖 clone 时未写入的 remote.fetch 配置
            gitRunner.run(repoDir, List.of("fetch", "origin",
                    "+refs/heads/" + branch + ":refs/heads/" + branch));
        } catch (GitCommandException e) {
            throw exception(BARE_REPO_FETCH_FAILED, projectCode, branch, GitErrorNormalizer.detail(e));
        }
    }

    private ReentrantLock lockFor(String projectCode) {
        return projectLocks.computeIfAbsent(projectCode, key -> new ReentrantLock());
    }

    private void validateProjectCode(String projectCode) {
        if (projectCode == null) {
            throw exception(BARE_REPO_PROJECT_CODE_INVALID, projectCode);
        }
        String value = projectCode.trim();
        if (value.isEmpty() || value.length() > 64
                || !value.matches(PROJECT_CODE_PATTERN)
                || value.contains("..")
                || value.endsWith(".")) {
            throw exception(BARE_REPO_PROJECT_CODE_INVALID, projectCode);
        }
    }

    private void validateBranch(String projectCode, String branch) {
        if (!GitRefs.isValidBranchName(branch)) {
            throw exception(BARE_REPO_BRANCH_INVALID, projectCode, branch);
        }
    }

    private boolean isBareRepository(Path dir) {
        return Files.isDirectory(dir)
                && Files.isRegularFile(dir.resolve("HEAD"))
                && Files.isDirectory(dir.resolve("objects"))
                && Files.isDirectory(dir.resolve("refs"))
                && !Files.exists(dir.resolve(".git"));
    }

}
