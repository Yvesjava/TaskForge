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
 * {@link TaskBranchManager} 的单元测试类
 *
 * <p>使用真实 git 子进程与 {@code @TempDir} 验证任务特性分支的
 * 命名、基线校验、创建、推送与幂等清理。
 */
class BranchTest {

    @TempDir
    Path tempDir;

    private final GitCommandRunner gitRunner = new GitCommandRunner();

    @Test
    void featureBranchName_generatesFeatureBranchFromTaskNo() {
        TaskBranchManager manager = manager(tempDir.resolve("cache"));

        assertThat(manager.featureBranchName("TASK-20261001-088"))
                .isEqualTo("feature/TASK-20261001-088");
    }

    @Test
    void featureBranchName_rejectsInvalidTaskNo() {
        TaskBranchManager manager = manager(tempDir.resolve("cache"));

        assertThatThrownBy(() -> manager.featureBranchName("bad taskNo"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.BRANCH_TASK_NO_INVALID.getCode()));
    }

    @Test
    void validateBaseline_resolvesLatestBaselineCommit() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        TaskBranchManager manager = manager(tempDir.resolve("cache"));

        BranchOperation operation = manager.validateBaseline(
                "backend-service", source.toString(), "main");

        assertThat(operation.action()).isEqualTo(TaskBranchManager.ACTION_VALIDATE_BASELINE);
        assertThat(operation.projectCode()).isEqualTo("backend-service");
        assertThat(operation.branch()).isEqualTo("main");
        assertThat(operation.baseline()).isEqualTo("main");
        assertThat(operation.commit()).isEqualTo(revParse(source, "refs/heads/main"));
    }

    @Test
    void validateBaseline_rejectsMissingBaseline() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        TaskBranchManager manager = manager(tempDir.resolve("cache"));

        assertThatThrownBy(() -> manager.validateBaseline(
                "backend-service", source.toString(), "missing"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.BRANCH_BASELINE_NOT_FOUND.getCode()));
    }

    @Test
    void validateBaseline_rejectsInvalidBranchName() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        TaskBranchManager manager = manager(tempDir.resolve("cache"));

        assertThatThrownBy(() -> manager.validateBaseline(
                "backend-service", source.toString(), "bad branch"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.BRANCH_NAME_INVALID.getCode()));
    }

    @Test
    void createFeatureBranch_createsBranchAtBaselineCommit() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        TaskBranchManager manager = manager(tempDir.resolve("cache"));

        BranchOperation operation = manager.createFeatureBranch(
                "backend-service", source.toString(), "main", "feature/TASK-20261001-088");

        String baseline = revParse(source, "refs/heads/main");
        assertThat(operation.action()).isEqualTo(TaskBranchManager.ACTION_CREATE_BRANCH);
        assertThat(operation.branch()).isEqualTo("feature/TASK-20261001-088");
        assertThat(operation.baseline()).isEqualTo("main");
        assertThat(operation.commit()).isEqualTo(baseline);
        assertThat(revParse(bareRepoPath(tempDir.resolve("cache"), "backend-service"),
                "refs/heads/feature/TASK-20261001-088")).isEqualTo(baseline);
    }

    @Test
    void createFeatureBranch_rejectsInvalidTargetBranch() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        TaskBranchManager manager = manager(tempDir.resolve("cache"));

        assertThatThrownBy(() -> manager.createFeatureBranch(
                "backend-service", source.toString(), "main", "bad branch"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.BRANCH_NAME_INVALID.getCode()));
    }

    @Test
    void pushFeatureBranch_pushesBranchToRemote() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        TaskBranchManager manager = manager(tempDir.resolve("cache"));
        manager.createFeatureBranch("backend-service", source.toString(),
                "main", "feature/TASK-20261001-088");

        BranchOperation operation = manager.pushFeatureBranch(
                "backend-service", "feature/TASK-20261001-088");

        assertThat(operation.action()).isEqualTo(TaskBranchManager.ACTION_PUSH_BRANCH);
        assertThat(operation.remote()).isEqualTo("origin");
        assertThat(operation.commit()).isEqualTo(revParse(source, "refs/heads/main"));
        assertThat(revParse(source, "refs/heads/feature/TASK-20261001-088"))
                .isEqualTo(revParse(source, "refs/heads/main"));
    }

    @Test
    void deleteFeatureBranch_removesLocalAndRemoteIdempotently() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source);
        TaskBranchManager manager = manager(tempDir.resolve("cache"));
        String branch = "feature/TASK-20261001-088";
        manager.createFeatureBranch("backend-service", source.toString(), "main", branch);
        manager.pushFeatureBranch("backend-service", branch);

        List<BranchOperation> operations = manager.deleteFeatureBranch("backend-service", branch);

        assertThat(operations).hasSize(2);
        assertThat(operations).extracting(BranchOperation::action)
                .containsExactlyInAnyOrder(
                        TaskBranchManager.ACTION_DELETE_LOCAL_BRANCH,
                        TaskBranchManager.ACTION_DELETE_REMOTE_BRANCH);
        assertThat(resolveRef(source, "refs/heads/" + branch)).isNull();
        assertThat(resolveRef(bareRepoPath(tempDir.resolve("cache"), "backend-service"),
                "refs/heads/" + branch)).isNull();

        // 幂等：第二次清理不再重复删除，直接返回空结果
        assertThat(manager.deleteFeatureBranch("backend-service", branch)).isEmpty();
    }

    private TaskBranchManager manager(Path cacheRoot) {
        BareRepoManager bareRepoManager = new BareRepoManager(properties(cacheRoot), gitRunner);
        return new TaskBranchManager(bareRepoManager, gitRunner);
    }

    private AgentWorkspaceProperties properties(Path cacheRoot) {
        AgentWorkspaceProperties properties = new AgentWorkspaceProperties();
        properties.setBareRepoRoot(cacheRoot.toString());
        return properties;
    }

    private Path bareRepoPath(Path cacheRoot, String projectCode) {
        return cacheRoot.resolve(projectCode + ".git");
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

    private String resolveRef(Path repo, String ref) {
        try {
            return gitRunner.run(repo, List.of("rev-parse", "--verify", ref)).trim();
        } catch (RuntimeException e) {
            return null;
        }
    }

}
