package cn.iocoder.yudao.module.agent.service.exec;

import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.framework.exec.AgentExecProperties;
import cn.iocoder.yudao.module.agent.framework.exec.CodexRetryRunner;
import cn.iocoder.yudao.module.agent.framework.exec.CodexRunner;
import cn.iocoder.yudao.module.agent.framework.exec.CodexRunnerTestHelper;
import cn.iocoder.yudao.module.agent.framework.exec.CommandGate;
import cn.iocoder.yudao.module.agent.framework.observability.AgentObservability;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskStateMachineImpl;
import cn.iocoder.yudao.module.agent.service.workspace.CompositeWorkspace;
import cn.iocoder.yudao.module.agent.service.workspace.TaskBranchManager;
import cn.iocoder.yudao.module.agent.service.workspace.WorktreeManager;
import cn.iocoder.yudao.module.agent.service.workspace.WorkspaceProject;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-EXEC-05 超时熔断契约测试
 *
 * <p>使用真实 JVM 子进程充当假 Codex，验证超时后 {@link CodexRunner} 终止完整
 * 进程组（父进程与其派生子进程），并且 {@link AgentTaskExecutor} 将任务写回为
 * {@code FAILED}，同时执行失败清理且不触发后续自修复重试。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskTimeoutTest {

    private static final Long TASK_ID = 9021L;

    private static final String TASK_NO = "TASK-20261003-021";

    private static final String TARGET_BRANCH = "feature/TASK-20261003-021";

    private static final String WORKER_ID = "worker-timeout-test";

    private static final Long GENERATION = 7L;

    @TempDir
    Path tempDir;

    @Mock
    private WorktreeManager worktreeManager;

    @Mock
    private TaskBranchManager taskBranchManager;

    @Mock
    private CommandGate commandGate;

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskOperationLogMapper operationLogMapper;

    @Test
    void execute_timesOutAndTerminatesWholeProcessGroupThenMarksFailed() throws Exception {
        Path pidFile = tempDir.resolve("pids.txt");
        AgentExecProperties properties = new AgentExecProperties();
        CodexRunner runner = new CodexRunner(properties);
        CodexRetryRunner retryRunner = new CodexRetryRunner(runner, properties);

        AgentTaskStateMachineImpl stateMachine = new AgentTaskStateMachineImpl();
        ReflectionTestUtils.setField(stateMachine, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(stateMachine, "operationLogMapper", operationLogMapper);

        AgentTaskExecutor executor = new AgentTaskExecutor(
                worktreeManager, taskBranchManager, retryRunner, commandGate, stateMachine,
                new AgentObservability(new SimpleMeterRegistry()));

        CompositeWorkspace workspace = CompositeWorkspace.builder()
                .taskNo(TASK_NO)
                .targetBranch(TARGET_BRANCH)
                .root(tempDir)
                .projects(List.of())
                .build();
        when(worktreeManager.createCompositeWorkspace(eq(TASK_NO), eq(TARGET_BRANCH), any(), anyList()))
                .thenReturn(workspace);
        when(taskMapper.markFailedIfRunning(eq(TASK_ID), eq(WORKER_ID), eq(GENERATION),
                anyString(), eq(0), anyLong(), isNull())).thenReturn(1);

        WorkspaceProject project = WorkspaceProject.builder()
                .projectCode("demo")
                .gitUrl("git@example.com/demo.git")
                .baseBranch("main")
                .subDir("backend")
                .build();

        ExecutionOutcome outcome = executor.execute(ExecutionRequest.builder()
                .taskId(TASK_ID)
                .taskNo(TASK_NO)
                .targetBranch(TARGET_BRANCH)
                .taskDoc("doc")
                .workerId(WORKER_ID)
                .generation(GENERATION)
                .timeout(Duration.ofSeconds(8))
                .projects(List.of(project))
                .verificationCommands(List.of())
                .executable(javaExecutable())
                .arguments(List.of("-cp", System.getProperty("java.class.path"),
                        CodexRunnerTestHelper.class.getName(), "spawn-child", pidFile.toString()))
                .build());

        assertThat(outcome.status()).isEqualTo(AgentTaskStatus.FAILED);
        assertThat(outcome.timedOut()).isTrue();
        assertThat(outcome.retryTimes()).isZero();
        assertThat(outcome.executionLog()).contains("=== Codex attempts ===").contains("timedOut=true");

        String pids = Files.readString(pidFile);
        assertThat(pids).isNotBlank();
        String[] parts = pids.trim().split("\\s+");
        awaitTerminated(Long.parseLong(parts[0]));
        awaitTerminated(Long.parseLong(parts[1]));

        verify(worktreeManager).destroyCompositeWorkspaceIfPresent(TASK_NO);
        verify(taskBranchManager).deleteFeatureBranch("demo", TARGET_BRANCH);

        ArgumentCaptor<AgentTaskOperationLogDO> auditCaptor = ArgumentCaptor.forClass(AgentTaskOperationLogDO.class);
        verify(operationLogMapper).insert(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("TIMEOUT");
        assertThat(auditCaptor.getValue().getFromStatus()).isEqualTo("RUNNING");
        assertThat(auditCaptor.getValue().getToStatus()).isEqualTo("FAILED");
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
