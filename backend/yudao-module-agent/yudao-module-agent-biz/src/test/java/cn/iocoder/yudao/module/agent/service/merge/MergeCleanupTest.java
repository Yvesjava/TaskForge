package cn.iocoder.yudao.module.agent.service.merge;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskProjectMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskStateMachine;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskTransitionCommand;
import cn.iocoder.yudao.module.agent.service.workspace.BareRepoManager;
import cn.iocoder.yudao.module.agent.service.workspace.TaskBranchManager;
import cn.iocoder.yudao.module.agent.service.workspace.WorktreeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MERGE-05 合并完成后资源清理测试。
 *
 * <p>覆盖全量清理顺序、清理失败保留 {@code ACCEPTED}、已 {@code COMPLETED} 幂等、
 * 非 {@code ACCEPTED} 状态拒绝，以及未全仓合并时拒绝收尾，验证“全部资源清理完成后
 * 才进入 {@code COMPLETED}”。</p>
 */
@ExtendWith(MockitoExtension.class)
class MergeCleanupTest {

    private static final Long TASK_ID = 9013L;
    private static final String KEY = "cleanup_20261003_0001";
    private static final String TASK_NO = "TASK-20261003-089";
    private static final String TARGET_BRANCH = "feature/TASK-20261003-089";

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskProjectMapper taskProjectMapper;

    @Mock
    private AgentTaskStateMachine stateMachine;

    @Mock
    private WorktreeManager worktreeManager;

    @Mock
    private TaskBranchManager taskBranchManager;

    @Mock
    private BareRepoManager bareRepoManager;

    private MergeCleanupService service;

    @BeforeEach
    void setUp() {
        service = new MergeCleanupService(taskMapper, taskProjectMapper, stateMachine,
                worktreeManager, taskBranchManager, bareRepoManager);
    }

    @Test
    void cleanupAfterMerge_cleansWorktreeBranchesCacheThenCompletes() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of(
                project("repo-a", "MERGED"), project("repo-b", "MERGED")));
        when(stateMachine.transition(any())).thenReturn(AgentTaskStatus.COMPLETED);

        InOrder inOrder = inOrder(worktreeManager, taskBranchManager, bareRepoManager, stateMachine);
        AgentTaskStatus result = service.cleanup(command());

        assertThat(result).isEqualTo(AgentTaskStatus.COMPLETED);

        inOrder.verify(worktreeManager).destroyCompositeWorkspaceIfPresent(TASK_NO);
        inOrder.verify(taskBranchManager).deleteFeatureBranch("repo-a", TARGET_BRANCH);
        inOrder.verify(taskBranchManager).deleteFeatureBranch("repo-b", TARGET_BRANCH);
        inOrder.verify(bareRepoManager).deleteRepositoryIfPresent("repo-a");
        inOrder.verify(bareRepoManager).deleteRepositoryIfPresent("repo-b");
        inOrder.verify(stateMachine).transition(any());

        AgentTaskTransitionCommand transition = captureTransition();
        assertThat(transition.getAction()).isEqualTo(AgentTaskAction.MERGE_PASS);
        assertThat(transition.getFromStatus()).isEqualTo(AgentTaskStatus.ACCEPTED);
        assertThat(transition.getRequestIdempotencyKey()).isEqualTo(KEY);
    }

    @Test
    void cleanupFailure_keepsTaskAcceptedAndDoesNotComplete() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of(
                project("repo-a", "MERGED")));
        doThrow(new RuntimeException("worktree cleanup boom"))
                .when(worktreeManager).destroyCompositeWorkspaceIfPresent(TASK_NO);

        assertThatThrownBy(() -> service.cleanup(command()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("worktree cleanup boom");

        verify(taskBranchManager, never()).deleteFeatureBranch(any(), any());
        verify(bareRepoManager, never()).deleteRepositoryIfPresent(any());
        verify(stateMachine, never()).transition(any());
    }

    @Test
    void alreadyCompletedTask_isIdempotentNoop() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("COMPLETED"));

        AgentTaskStatus result = service.cleanup(command());

        assertThat(result).isEqualTo(AgentTaskStatus.COMPLETED);
        verify(worktreeManager, never()).destroyCompositeWorkspaceIfPresent(any());
        verify(taskBranchManager, never()).deleteFeatureBranch(any(), any());
        verify(bareRepoManager, never()).deleteRepositoryIfPresent(any());
        verify(stateMachine, never()).transition(any());
    }

    @Test
    void nonAcceptedTask_isRejected() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("WAITING_ACCEPTANCE"));

        assertThatThrownBy(() -> service.cleanup(command()))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.MERGE_TASK_STATE_INVALID.getCode()));

        verify(worktreeManager, never()).destroyCompositeWorkspaceIfPresent(any());
        verify(stateMachine, never()).transition(any());
    }

    @Test
    void notAllReposMerged_doesNotComplete() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of(
                project("repo-a", "MERGED"), project("repo-b", "UNMERGED")));

        assertThatThrownBy(() -> service.cleanup(command()))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.MERGE_CLEANUP_REPOS_NOT_MERGED.getCode()));

        verify(worktreeManager, never()).destroyCompositeWorkspaceIfPresent(any());
        verify(stateMachine, never()).transition(any());
    }

    @Test
    void cleanupWithoutTargetBranch_skipsBranchCleanupButCompletes() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(taskWithoutBranch("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of(
                project("repo-a", "MERGED")));
        when(stateMachine.transition(any())).thenReturn(AgentTaskStatus.COMPLETED);

        AgentTaskStatus result = service.cleanup(command());

        assertThat(result).isEqualTo(AgentTaskStatus.COMPLETED);
        verify(worktreeManager).destroyCompositeWorkspaceIfPresent(TASK_NO);
        verify(taskBranchManager, never()).deleteFeatureBranch(any(), any());
        verify(bareRepoManager).deleteRepositoryIfPresent("repo-a");
        verify(stateMachine).transition(any());
    }

    private MergeCleanupCommand command() {
        return MergeCleanupCommand.of(TASK_ID, KEY);
    }

    private AgentTaskTransitionCommand captureTransition() {
        ArgumentCaptor<AgentTaskTransitionCommand> captor =
                ArgumentCaptor.forClass(AgentTaskTransitionCommand.class);
        verify(stateMachine).transition(captor.capture());
        return captor.getValue();
    }

    private static AgentTaskDO task(String status) {
        return AgentTaskDO.builder()
                .id(TASK_ID)
                .taskNo(TASK_NO)
                .status(status)
                .targetBranch(TARGET_BRANCH)
                .docVersion(3)
                .build();
    }

    private static AgentTaskDO taskWithoutBranch(String status) {
        return AgentTaskDO.builder()
                .id(TASK_ID)
                .taskNo(TASK_NO)
                .status(status)
                .targetBranch(null)
                .docVersion(3)
                .build();
    }

    private static AgentTaskProjectDO project(String projectCode, String mergeStatus) {
        return AgentTaskProjectDO.builder()
                .id(1L)
                .taskId(TASK_ID)
                .projectCode(projectCode)
                .baseBranch("main")
                .subDir(projectCode)
                .mergeStatus(mergeStatus)
                .commitHash("sha-" + projectCode)
                .build();
    }

}
