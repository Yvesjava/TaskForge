package cn.iocoder.yudao.module.agent.e2e;

import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.framework.exec.AgentExecProperties;
import cn.iocoder.yudao.module.agent.framework.exec.CodexRetryRunner;
import cn.iocoder.yudao.module.agent.framework.exec.CodexRunner;
import cn.iocoder.yudao.module.agent.framework.exec.CodexRunnerTestHelper;
import cn.iocoder.yudao.module.agent.framework.exec.CommandGate;
import cn.iocoder.yudao.module.agent.framework.exec.ProcessCommandExecutor;
import cn.iocoder.yudao.module.agent.framework.observability.AgentObservability;
import cn.iocoder.yudao.module.agent.framework.workspace.config.AgentWorkspaceProperties;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandRunner;
import cn.iocoder.yudao.module.agent.service.exec.AgentTaskExecutor;
import cn.iocoder.yudao.module.agent.service.exec.ExecutionOutcome;
import cn.iocoder.yudao.module.agent.service.exec.ExecutionRequest;
import cn.iocoder.yudao.module.agent.service.project.AgentProjectRefValidator;
import cn.iocoder.yudao.module.agent.service.security.SecurityPolicy;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskStateMachineImpl;
import cn.iocoder.yudao.module.agent.service.workspace.BareRepoManager;
import cn.iocoder.yudao.module.agent.service.workspace.TaskBranchManager;
import cn.iocoder.yudao.module.agent.service.workspace.WorkflowInjector;
import cn.iocoder.yudao.module.agent.service.workspace.WorktreeManager;
import cn.iocoder.yudao.module.agent.service.workspace.WorkspaceProject;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TASK-QA-03 端到端执行测试（真实 Worktree + 假 Codex + 超时恢复）。
 *
 * <p>使用真实 {@code git} 子进程构建聚合工作区，再用真实 JVM 子进程充当假 Codex，
 * 覆盖执行成功链路与超时熔断链路。数据库边界（MyBatis Mapper）是唯一被 mock 的
 * 外部依赖，其余编排组件（工作区、分支、命令门禁、安全策略、状态机、执行器）均
 * 使用真实实现。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentExecutionE2eTest {

    private static final Long TASK_ID = 9091L;

    private static final String TASK_NO = "TASK-QA-03-E2E-001";

    private static final String TARGET_BRANCH = "feature/TASK-QA-03-E2E-001";

    private static final String WORKER_ID = "worker-e2e";

    private static final Long GENERATION = 11L;

    @TempDir
    Path tempDir;

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskOperationLogMapper operationLogMapper;

    @Mock
    private AgentProjectRefValidator projectRefValidator;

    private GitCommandRunner gitRunner;

    private AgentWorkspaceProperties workspaceProperties;

    private BareRepoManager bareRepoManager;

    private TaskBranchManager taskBranchManager;

    private WorktreeManager worktreeManager;

    private AgentTaskExecutor executor;

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

        AgentExecProperties execProperties = new AgentExecProperties();
        CodexRunner codexRunner = new CodexRunner(execProperties);
        CodexRetryRunner retryRunner = new CodexRetryRunner(codexRunner, execProperties);
        CommandGate commandGate = new CommandGate(new ProcessCommandExecutor(execProperties), execProperties);
        SecurityPolicy securityPolicy = new SecurityPolicy(commandGate, projectRefValidator);

        AgentTaskStateMachineImpl stateMachine = new AgentTaskStateMachineImpl();
        ReflectionTestUtils.setField(stateMachine, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(stateMachine, "operationLogMapper", operationLogMapper);

        executor = new AgentTaskExecutor(worktreeManager, taskBranchManager, retryRunner, commandGate,
                securityPolicy, stateMachine, new AgentObservability(new SimpleMeterRegistry()));
    }

    @Test
    void execute_codexWritesFileAndSelfVerifiesSuccessfully() throws Exception {
        Path source = initRepo("source", "README.md", "base\n");
        when(taskMapper.markSelfVerifiedIfRunning(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        ExecutionOutcome outcome = executor.execute(ExecutionRequest.builder()
                .taskId(TASK_ID)
                .taskNo(TASK_NO)
                .targetBranch(TARGET_BRANCH)
                .taskDoc("# e2e happy path")
                .workerId(WORKER_ID)
                .generation(GENERATION)
                .projects(List.of(WorkspaceProject.builder()
                        .projectCode("backend-service")
                        .gitUrl(source.toString())
                        .baseBranch("main")
                        .subDir("backend")
                        .build()))
                .verificationCommands(List.of())
                .executable(javaExecutable())
                .arguments(List.of("-cp", System.getProperty("java.class.path"),
                        CodexRunnerTestHelper.class.getName(), "write-file",
                        "backend/generated.txt", "codex-output"))
                .build());

        assertThat(outcome.status()).isEqualTo(AgentTaskStatus.WAITING_ACCEPTANCE);
        assertThat(outcome.timedOut()).isFalse();
        assertThat(outcome.retryTimes()).isZero();

        Path workspaceRoot = workspaceRoot();
        assertThat(workspaceRoot.resolve("backend/generated.txt")).hasContent("codex-output");
        assertThat(workspaceRoot).exists();
    }

    @Test
    void execute_timeoutTerminatesProcessTreeAndRecoversResources() throws Exception {
        Path source = initRepo("source-timeout", "README.md", "base\n");
        Path pidFile = tempDir.resolve("pids.txt");
        when(taskMapper.markFailedIfRunning(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        ExecutionOutcome outcome = executor.execute(ExecutionRequest.builder()
                .taskId(TASK_ID)
                .taskNo("TASK-QA-03-E2E-002")
                .targetBranch("feature/TASK-QA-03-E2E-002")
                .taskDoc("# e2e timeout")
                .workerId(WORKER_ID)
                .generation(GENERATION)
                .timeout(Duration.ofSeconds(8))
                .projects(List.of(WorkspaceProject.builder()
                        .projectCode("backend-service")
                        .gitUrl(source.toString())
                        .baseBranch("main")
                        .subDir("backend")
                        .build()))
                .verificationCommands(List.of())
                .executable(javaExecutable())
                .arguments(List.of("-cp", System.getProperty("java.class.path"),
                        CodexRunnerTestHelper.class.getName(), "spawn-child", pidFile.toString()))
                .build());

        assertThat(outcome.status()).isEqualTo(AgentTaskStatus.FAILED);
        assertThat(outcome.timedOut()).isTrue();
        assertThat(outcome.retryTimes()).isZero();
        assertThat(outcome.executionLog()).contains("timedOut=true");

        String pids = Files.readString(pidFile);
        assertThat(pids).isNotBlank();
        String[] parts = pids.trim().split("\\s+");
        awaitTerminated(Long.parseLong(parts[0]));
        awaitTerminated(Long.parseLong(parts[1]));

        assertThat(tempDir.resolve("workspace").resolve("dirA-TASK-QA-03-E2E-002")).doesNotExist();
        assertThat(resolveRef(bareRepoManager.repositoryPath("backend-service"),
                "refs/heads/feature/TASK-QA-03-E2E-002")).isNull();
    }

    private Path workspaceRoot() {
        return tempDir.resolve("workspace").resolve("dirA-" + TASK_NO);
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

    private String resolveRef(Path repo, String ref) {
        try {
            return gitRunner.run(repo, List.of("rev-parse", "--verify", ref)).trim();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void awaitTerminated(long pid) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(15);
        while (System.currentTimeMillis() < deadline) {
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            if (handle.isEmpty() || !handle.get().isAlive()) {
                return;
            }
            Thread.sleep(100);
        }
        fail("进程 " + pid + " 未在超时前终止");
    }

    private String javaExecutable() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String name = os.contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", name).toString();
    }

}
