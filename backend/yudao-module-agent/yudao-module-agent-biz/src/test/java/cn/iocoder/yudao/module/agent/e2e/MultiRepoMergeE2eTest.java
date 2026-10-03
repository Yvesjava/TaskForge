package cn.iocoder.yudao.module.agent.e2e;

import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskProjectMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiCredentials;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiOperator;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiOperatorRegistry;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeOptions;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeRequest;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeRequestRef;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeResult;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeState;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeStatus;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitPlatform;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitRepository;
import cn.iocoder.yudao.module.agent.framework.workspace.config.AgentWorkspaceProperties;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandException;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandRunner;
import cn.iocoder.yudao.module.agent.service.merge.MergeCommand;
import cn.iocoder.yudao.module.agent.service.merge.MergeOutcome;
import cn.iocoder.yudao.module.agent.service.merge.MergeRepoResult;
import cn.iocoder.yudao.module.agent.service.merge.MergeService;
import cn.iocoder.yudao.module.agent.service.merge.MergeTarget;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskStateMachine;
import cn.iocoder.yudao.module.agent.service.workspace.BareRepoManager;
import cn.iocoder.yudao.module.agent.service.workspace.CompositeWorkspace;
import cn.iocoder.yudao.module.agent.service.workspace.TaskBranchManager;
import cn.iocoder.yudao.module.agent.service.workspace.WorkflowInjector;
import cn.iocoder.yudao.module.agent.service.workspace.WorktreeManager;
import cn.iocoder.yudao.module.agent.service.workspace.WorkspaceProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-QA-03 多仓合并端到端测试（真实 git 合并）。
 *
 * <p>在本地 git 源仓库上构建两个项目的聚合工作区，模拟 Codex 在各自特性分支上提交，
 * 推送后通过一个执行真实 {@code git merge --ff-only} 的 {@link GitApiOperator} 完成
 * 多仓 Fast-Forward 合并，验证各仓 {@code main} 确实推进到特性提交；并覆盖单仓冲突时
 * 阻断合并、转人工处理的行为。数据库边界（MyBatis Mapper）被 mock。</p>
 */
@ExtendWith(MockitoExtension.class)
class MultiRepoMergeE2eTest {

    private static final Long TASK_ID = 9092L;

    private static final String TASK_NO = "TASK-QA-03-E2E-MERGE";

    private static final String TARGET_BRANCH = "feature/TASK-QA-03-E2E-MERGE";

    private static final String KEY = "merge_e2e_0001";

    private static final GitApiCredentials CREDENTIALS = GitApiCredentials.of("token");

    @TempDir
    Path tempDir;

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskProjectMapper taskProjectMapper;

    @Mock
    private AgentTaskOperationLogMapper operationLogMapper;

    @Mock
    private AgentTaskStateMachine stateMachine;

    private GitCommandRunner gitRunner;

    private AgentWorkspaceProperties workspaceProperties;

    private BareRepoManager bareRepoManager;

    private TaskBranchManager taskBranchManager;

    private WorktreeManager worktreeManager;

    @BeforeEach
    void setUp() {
        gitRunner = new GitCommandRunner();
        workspaceProperties = new AgentWorkspaceProperties();
        workspaceProperties.setBareRepoRoot(tempDir.resolve("bare").toString());
        workspaceProperties.setWorkspaceRoot(tempDir.resolve("workspace").toString());
        bareRepoManager = new BareRepoManager(workspaceProperties, gitRunner);
        taskBranchManager = new TaskBranchManager(bareRepoManager, gitRunner);
        worktreeManager = new WorktreeManager(workspaceProperties, bareRepoManager, gitRunner,
                new WorkflowInjector());
    }

    @Test
    void mergeAllRepos_fastForwardsFeatureBranchesIntoMain() throws Exception {
        Fixture fixture = fixture(false);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of());

        MergeService service = new MergeService(
                new GitApiOperatorRegistry(List.of(operator(fixture))),
                taskMapper, taskProjectMapper, operationLogMapper, stateMachine);

        MergeOutcome outcome = service.merge(MergeCommand.of(TASK_ID, KEY, targets()));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.taskStatus()).isEqualTo("ACCEPTED");
        assertThat(outcome.repoResults()).allSatisfy(result -> assertThat(result.merged()).isTrue());
        assertThat(mainOf(fixture.backend())).isEqualTo(featureOf(fixture.backend()));
        assertThat(mainOf(fixture.frontend())).isEqualTo(featureOf(fixture.frontend()));
        verify(taskProjectMapper).updateMergeResult(TASK_ID, "backend-service", "MERGED", mainOf(fixture.backend()));
        verify(taskProjectMapper).updateMergeResult(TASK_ID, "frontend-portal", "MERGED", mainOf(fixture.frontend()));
    }

    @Test
    void anyRepoConflict_transitionsToMergeConflictPendingManual() throws Exception {
        Fixture fixture = fixture(true);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of());
        when(stateMachine.transition(any())).thenReturn(AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL);

        MergeService service = new MergeService(
                new GitApiOperatorRegistry(List.of(operator(fixture))),
                taskMapper, taskProjectMapper, operationLogMapper, stateMachine);

        MergeOutcome outcome = service.merge(MergeCommand.of(TASK_ID, KEY, targets()));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.taskStatus()).isEqualTo("MERGE_CONFLICT_PENDING_MANUAL");
        assertThat(outcome.repoResults()).extracting(MergeRepoResult::mergeStatus)
                .containsExactly("MERGED", "FAILED");
        assertThat(mainOf(fixture.backend())).isEqualTo(featureOf(fixture.backend()));
        assertThat(mainOf(fixture.frontend())).isNotEqualTo(featureOf(fixture.frontend()));
    }

    private Fixture fixture(boolean divergeFrontend) throws Exception {
        Path backend = initRepo("backend-src", "pom.xml", "<project/>\n");
        Path frontend = initRepo("frontend-src", "package.json", "{}\n");

        CompositeWorkspace workspace = worktreeManager.createCompositeWorkspace(
                TASK_NO, TARGET_BRANCH, "# merge e2e",
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

        commitFile(workspace.getRoot().resolve("backend"), "backend.txt", "backend change\n",
                "feat: backend change");
        commitFile(workspace.getRoot().resolve("frontend"), "frontend.txt", "frontend change\n",
                "feat: frontend change");

        taskBranchManager.pushFeatureBranch("backend-service", TARGET_BRANCH);
        taskBranchManager.pushFeatureBranch("frontend-portal", TARGET_BRANCH);

        if (divergeFrontend) {
            commitFile(frontend, "main-only.txt", "main moved on\n", "chore: main moved on");
        }

        return new Fixture(backend, frontend);
    }

    private LocalGitOperator operator(Fixture fixture) {
        return new LocalGitOperator(Map.of(
                "backend-service", fixture.backend(),
                "frontend-portal", fixture.frontend()));
    }

    private List<MergeTarget> targets() {
        return List.of(target("backend-service"), target("frontend-portal"));
    }

    private MergeTarget target(String repoKey) {
        return MergeTarget.of(repoKey,
                GitRepository.of(GitPlatform.GITHUB, "github.com", "org", repoKey),
                CREDENTIALS,
                new GitMergeRequestRef(42, "https://github.com/org/" + repoKey + "/pull/42"),
                GitMergeOptions.fastForward());
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

    private void commitFile(Path repo, String fileName, String content, String message) throws Exception {
        Files.writeString(repo.resolve(fileName), content);
        gitRunner.run(repo, List.of("add", "."));
        gitRunner.run(repo, List.of("-c", "user.email=test@example.com", "-c", "user.name=test",
                "commit", "-m", message));
    }

    private String mainOf(Path repo) {
        return gitRunner.run(repo, List.of("rev-parse", "refs/heads/main")).trim();
    }

    private String featureOf(Path repo) {
        return gitRunner.run(repo, List.of("rev-parse", "refs/heads/" + TARGET_BRANCH)).trim();
    }

    private AgentTaskDO task(String status) {
        return AgentTaskDO.builder()
                .id(TASK_ID)
                .taskNo(TASK_NO)
                .status(status)
                .docVersion(3)
                .executionGeneration(1L)
                .build();
    }

    private record Fixture(Path backend, Path frontend) {
    }

    /**
     * 在本地源仓库上执行真实 Fast-Forward 合并的假 Git 平台算子。
     */
    private final class LocalGitOperator implements GitApiOperator {

        private final Map<String, Path> remotes;

        LocalGitOperator(Map<String, Path> remotes) {
            this.remotes = Map.copyOf(remotes);
        }

        @Override
        public GitPlatform platform() {
            return GitPlatform.GITHUB;
        }

        @Override
        public GitMergeRequestRef createMergeRequest(GitRepository repository,
                                                     GitApiCredentials credentials,
                                                     GitMergeRequest request) {
            return new GitMergeRequestRef(1, "");
        }

        @Override
        public GitMergeStatus queryMergeRequest(GitRepository repository,
                                                GitApiCredentials credentials,
                                                GitMergeRequestRef ref) {
            return new GitMergeStatus(GitMergeState.OPEN, true, "", "", "feature");
        }

        @Override
        public GitMergeResult merge(GitRepository repository, GitApiCredentials credentials,
                                    GitMergeRequestRef ref, GitMergeOptions options) {
            Path remote = remotes.get(repository.name());
            try {
                gitRunner.run(remote, List.of("merge", "--ff-only", TARGET_BRANCH));
                String sha = gitRunner.run(remote, List.of("rev-parse", "HEAD")).trim();
                return GitMergeResult.success(sha, ref.webUrl());
            } catch (GitCommandException e) {
                return GitMergeResult.failure(GitMergeState.UNKNOWN, ref.webUrl(), "自动合并失败");
            }
        }

    }

}
