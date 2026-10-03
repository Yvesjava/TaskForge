package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskResetRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskProjectMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.agent.framework.workspace.config.AgentWorkspaceProperties;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandRunner;
import cn.iocoder.yudao.module.agent.service.workspace.BareRepoManager;
import cn.iocoder.yudao.module.agent.service.workspace.TaskBranchManager;
import cn.iocoder.yudao.module.agent.service.workspace.WorkflowInjector;
import cn.iocoder.yudao.module.agent.service.workspace.WorktreeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AgentTaskResetService} 的单元测试
 *
 * <p>覆盖重置编排（PAUSED → RESETTING → PENDING/PAUSED）、非法状态拒绝、
 * 清理失败保留 RESETTING，以及“资源不存在仍完成元数据清理”的幂等语义。
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskResetTest {

    private static final Long TASK_ID = 9012L;
    private static final String TASK_NO = "TASK-20261001-088";
    private static final String TARGET_BRANCH = "feature/TASK-20261001-088";
    private static final String IDEMPOTENCY_KEY = "reset-idempotency-key-0001";

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskOperationLogMapper operationLogMapper;

    @Mock
    private AgentTaskStateMachine stateMachine;

    @Mock
    private ResetCleanupDelegate cleanupDelegate;

    @InjectMocks
    private AgentTaskResetServiceImpl resetService;

    @Test
    void reset_returnsToPendingAndCompletesMetadataCleanup() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(pausedTask());
        when(stateMachine.transition(any(AgentTaskTransitionCommand.class)))
                .thenReturn(AgentTaskStatus.RESETTING, AgentTaskStatus.PENDING);

        AgentTaskResetRespVO response = resetService.reset(TASK_ID, false, IDEMPOTENCY_KEY);

        assertThat(response.getTaskId()).isEqualTo(TASK_ID);
        assertThat(response.getTaskNo()).isEqualTo(TASK_NO);
        assertThat(response.getStatus()).isEqualTo(AgentTaskStatus.PENDING.getValue());

        List<AgentTaskTransitionCommand> commands = capturedTransitions();
        assertThat(commands).hasSize(2);
        assertThat(commands.get(0).getAction()).isEqualTo(AgentTaskAction.RESET);
        assertThat(commands.get(0).getFromStatus()).isEqualTo(AgentTaskStatus.PAUSED);
        assertThat(commands.get(1).getAction()).isEqualTo(AgentTaskAction.CLEANUP_PASS);
        assertThat(commands.get(1).getFromStatus()).isEqualTo(AgentTaskStatus.RESETTING);
        assertThat(commands.get(1).getTargetStatus()).isEqualTo(AgentTaskStatus.PENDING);

        verify(cleanupDelegate).cleanup(any(ResetCleanupContext.class));
    }

    @Test
    void reset_keepPausedReturnsToPaused() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(pausedTask());
        when(stateMachine.transition(any(AgentTaskTransitionCommand.class)))
                .thenReturn(AgentTaskStatus.RESETTING, AgentTaskStatus.PAUSED);

        AgentTaskResetRespVO response = resetService.reset(TASK_ID, true, IDEMPOTENCY_KEY);

        assertThat(response.getStatus()).isEqualTo(AgentTaskStatus.PAUSED.getValue());
        List<AgentTaskTransitionCommand> commands = capturedTransitions();
        assertThat(commands).hasSize(2);
        assertThat(commands.get(1).getTargetStatus()).isEqualTo(AgentTaskStatus.PAUSED);
        verify(cleanupDelegate).cleanup(any(ResetCleanupContext.class));
    }

    @Test
    void reset_rejectsWhenTaskMissing() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);

        assertThatThrownBy(() -> resetService.reset(TASK_ID, false, IDEMPOTENCY_KEY))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_NOT_FOUND.getCode()));
        verifyNoInteractions(stateMachine);
        verifyNoInteractions(cleanupDelegate);
    }

    @Test
    void reset_rejectsWhenTaskNotPaused() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(
                AgentTaskDO.builder().id(TASK_ID).taskNo(TASK_NO).status("RUNNING").build());

        assertThatThrownBy(() -> resetService.reset(TASK_ID, false, IDEMPOTENCY_KEY))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_CANNOT_RESET_NOT_PAUSED.getCode()));
        verifyNoInteractions(stateMachine);
        verifyNoInteractions(cleanupDelegate);
    }

    @Test
    void reset_keepsResettingWhenCleanupFails() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(pausedTask());
        when(stateMachine.transition(any(AgentTaskTransitionCommand.class)))
                .thenReturn(AgentTaskStatus.RESETTING);
        doThrow(new ServiceException(ErrorCodeConstants.WORKTREE_CLEANUP_FAILED))
                .when(cleanupDelegate).cleanup(any(ResetCleanupContext.class));

        assertThatThrownBy(() -> resetService.reset(TASK_ID, false, IDEMPOTENCY_KEY))
                .isInstanceOf(ServiceException.class);

        ArgumentCaptor<AgentTaskTransitionCommand> captor = ArgumentCaptor.forClass(AgentTaskTransitionCommand.class);
        verify(stateMachine).transition(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(AgentTaskAction.RESET);
        verify(cleanupDelegate).cleanup(any(ResetCleanupContext.class));
    }

    @Test
    void reset_completesMetadataCleanup_whenExternalResourcesAlreadyMissing(@TempDir Path tempDir) {
        // 真实清理委托：工作区目录与分支（含裸仓库缓存）均不存在，仍应幂等成功，
        // 使重置流程继续走到 CLEANUP_PASS 完成元数据清理。
        AgentTaskProjectMapper projectMapper = mock(AgentTaskProjectMapper.class);
        when(projectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of(
                AgentTaskProjectDO.builder().projectCode("backend-service").build()));

        GitCommandRunner gitRunner = new GitCommandRunner();
        BareRepoManager bareRepoManager = new BareRepoManager(
                properties(tempDir.resolve("bare"), tempDir.resolve("workspace")), gitRunner);
        WorktreeManager worktreeManager = new WorktreeManager(
                properties(tempDir.resolve("workspace"), tempDir.resolve("bare")),
                bareRepoManager, gitRunner, new WorkflowInjector());
        TaskBranchManager taskBranchManager = new TaskBranchManager(bareRepoManager, gitRunner);

        WorkspaceResetCleanupDelegate realDelegate = new WorkspaceResetCleanupDelegate();
        ReflectionTestUtils.setField(realDelegate, "worktreeManager", worktreeManager);
        ReflectionTestUtils.setField(realDelegate, "taskBranchManager", taskBranchManager);
        ReflectionTestUtils.setField(realDelegate, "taskProjectMapper", projectMapper);

        AgentTaskResetServiceImpl service = new AgentTaskResetServiceImpl();
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "operationLogMapper", operationLogMapper);
        ReflectionTestUtils.setField(service, "stateMachine", stateMachine);
        ReflectionTestUtils.setField(service, "cleanupDelegate", realDelegate);

        when(taskMapper.selectById(TASK_ID)).thenReturn(pausedTask());
        when(stateMachine.transition(any(AgentTaskTransitionCommand.class)))
                .thenReturn(AgentTaskStatus.RESETTING, AgentTaskStatus.PENDING);

        AgentTaskResetRespVO response = service.reset(TASK_ID, false, IDEMPOTENCY_KEY);

        assertThat(response.getStatus()).isEqualTo(AgentTaskStatus.PENDING.getValue());
        List<AgentTaskTransitionCommand> commands = capturedTransitions();
        assertThat(commands).hasSize(2);
        assertThat(commands.get(1).getAction()).isEqualTo(AgentTaskAction.CLEANUP_PASS);
        assertThat(commands.get(1).getTargetStatus()).isEqualTo(AgentTaskStatus.PENDING);
    }

    @Test
    void reset_replaysExistingIdempotencyKey() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY))
                .thenReturn(AgentTaskOperationLogDO.builder()
                        .taskId(TASK_ID)
                        .action(AgentTaskAction.RESET.getValue())
                        .build());
        when(taskMapper.selectById(TASK_ID)).thenReturn(pausedTask());

        AgentTaskResetRespVO response = resetService.reset(TASK_ID, false, IDEMPOTENCY_KEY);

        assertThat(response.getTaskId()).isEqualTo(TASK_ID);
        assertThat(response.getTaskNo()).isEqualTo(TASK_NO);
        assertThat(response.getStatus()).isEqualTo(AgentTaskStatus.PAUSED.getValue());
        verifyNoInteractions(stateMachine);
        verifyNoInteractions(cleanupDelegate);
    }

    private List<AgentTaskTransitionCommand> capturedTransitions() {
        ArgumentCaptor<AgentTaskTransitionCommand> captor = ArgumentCaptor.forClass(AgentTaskTransitionCommand.class);
        verify(stateMachine, times(2)).transition(captor.capture());
        return captor.getAllValues();
    }

    private AgentTaskDO pausedTask() {
        return AgentTaskDO.builder()
                .id(TASK_ID)
                .taskNo(TASK_NO)
                .status(AgentTaskStatus.PAUSED.getValue())
                .targetBranch(TARGET_BRANCH)
                .build();
    }

    private AgentWorkspaceProperties properties(Path workspaceRoot, Path bareRepoRoot) {
        AgentWorkspaceProperties properties = new AgentWorkspaceProperties();
        properties.setWorkspaceRoot(workspaceRoot.toString());
        properties.setBareRepoRoot(bareRepoRoot.toString());
        return properties;
    }

}
