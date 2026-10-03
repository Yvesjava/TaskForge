package cn.iocoder.yudao.module.agent.service.merge;

import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskProjectMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiCredentials;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiException;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiOperator;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiOperatorRegistry;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeRequest;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeOptions;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeRequestRef;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeResult;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeState;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeStatus;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitPlatform;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitRepository;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskStateMachine;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskTransitionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MERGE-03 Fast-Forward/Squash 合并与结果回写测试。
 *
 * <p>覆盖全仓成功、单仓失败、平台异常、逐仓幂等与重复回调幂等，验证各仓
 * {@code merge_status} 与任务状态保持一致，且不会发生重复合并。</p>
 */
@ExtendWith(MockitoExtension.class)
class MergeServiceTest {

    private static final Long TASK_ID = 9012L;
    private static final String KEY = "merge_20261003_0001";
    private static final GitApiCredentials CREDENTIALS = GitApiCredentials.of("token");

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskProjectMapper taskProjectMapper;

    @Mock
    private AgentTaskOperationLogMapper operationLogMapper;

    @Mock
    private AgentTaskStateMachine stateMachine;

    private RecordingOperator operator;
    private MergeService service;

    @BeforeEach
    void setUp() {
        operator = new RecordingOperator(GitPlatform.GITHUB);
        service = new MergeService(new GitApiOperatorRegistry(List.of(operator)),
                taskMapper, taskProjectMapper, operationLogMapper, stateMachine);
    }

    @Test
    void allReposMerge_successWritesBackAndKeepsTaskAccepted() {
        operator.whenMerge("repo-a", GitMergeResult.success("sha-a", "https://github.com/org/repo-a/pull/42"));
        operator.whenMerge("repo-b", GitMergeResult.success("sha-b", "https://github.com/org/repo-b/pull/42"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of());

        MergeOutcome outcome = service.merge(command(target("repo-a"), target("repo-b")));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.taskStatus()).isEqualTo("ACCEPTED");
        assertThat(outcome.repoResults())
                .extracting(MergeRepoResult::repoKey)
                .containsExactly("repo-a", "repo-b");
        assertThat(outcome.repoResults()).allSatisfy(result -> {
            assertThat(result.merged()).isTrue();
            assertThat(result.mergeStatus()).isEqualTo("MERGED");
        });
        assertThat(operator.mergeCalls()).isEqualTo(2);
        verify(taskProjectMapper).updateMergeResult(TASK_ID, "repo-a", "MERGED", "sha-a");
        verify(taskProjectMapper).updateMergeResult(TASK_ID, "repo-b", "MERGED", "sha-b");
        verify(stateMachine, never()).transition(any());
    }

    @Test
    void anyRepoMergeFailure_transitionsToMergeConflictPendingManual() {
        operator.whenMerge("repo-a", GitMergeResult.success("sha-a", "https://github.com/org/repo-a/pull/42"));
        operator.whenMerge("repo-b", GitMergeResult.failure(GitMergeState.UNKNOWN,
                "https://github.com/org/repo-b/pull/42", "目标分支出现冲突"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of());
        when(stateMachine.transition(any())).thenReturn(AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL);

        MergeOutcome outcome = service.merge(command(target("repo-a"), target("repo-b")));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.taskStatus()).isEqualTo("MERGE_CONFLICT_PENDING_MANUAL");
        assertThat(outcome.repoResults())
                .extracting(MergeRepoResult::mergeStatus)
                .containsExactly("MERGED", "FAILED");
        assertThat(outcome.repoResults().get(1).message()).isEqualTo("目标分支出现冲突");

        AgentTaskTransitionCommand transition = captureTransition();
        assertThat(transition.getAction()).isEqualTo(AgentTaskAction.MERGE_CONFLICT);
        assertThat(transition.getFromStatus()).isEqualTo(AgentTaskStatus.ACCEPTED);
        assertThat(transition.getRequestIdempotencyKey()).isEqualTo(KEY);

        verify(taskProjectMapper).updateMergeResult(TASK_ID, "repo-a", "MERGED", "sha-a");
        verify(taskProjectMapper).updateMergeResult(TASK_ID, "repo-b", "FAILED", "");
    }

    @Test
    void platformException_isRecordedAsFailedAndOtherReposContinue() {
        operator.whenMerge("repo-a", GitMergeResult.success("sha-a", ""));
        operator.whenMergeFails("repo-b", new GitApiException(500, "平台暂时不可用"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of());
        when(stateMachine.transition(any())).thenReturn(AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL);

        MergeOutcome outcome = service.merge(command(target("repo-a"), target("repo-b")));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.taskStatus()).isEqualTo("MERGE_CONFLICT_PENDING_MANUAL");
        assertThat(outcome.repoResults())
                .extracting(MergeRepoResult::mergeStatus)
                .containsExactly("MERGED", "FAILED");
        assertThat(outcome.repoResults().get(1).message()).contains("平台暂时不可用");
        assertThat(operator.mergeCalls()).isEqualTo(2);
    }

    @Test
    void duplicateSuccessCallback_isIdempotent() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of(
                project("repo-a", "MERGED", "sha-a"),
                project("repo-b", "MERGED", "sha-b")));

        MergeOutcome outcome = service.merge(command(target("repo-a"), target("repo-b")));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.taskStatus()).isEqualTo("ACCEPTED");
        assertThat(outcome.repoResults()).allSatisfy(result -> assertThat(result.merged()).isTrue());
        assertThat(operator.mergeCalls()).isZero();
        verify(taskProjectMapper, never()).updateMergeResult(any(), any(), any(), any());
        verify(stateMachine, never()).transition(any());
    }

    @Test
    void duplicateFailureCallback_isIdempotent() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, KEY))
                .thenReturn(operationLog("MERGE_CONFLICT", "ACCEPTED", "MERGE_CONFLICT_PENDING_MANUAL"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("MERGE_CONFLICT_PENDING_MANUAL"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of(
                project("repo-a", "MERGED", "sha-a"),
                project("repo-b", "FAILED", "")));

        MergeOutcome outcome = service.merge(command(target("repo-a"), target("repo-b")));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.taskStatus()).isEqualTo("MERGE_CONFLICT_PENDING_MANUAL");
        assertThat(outcome.repoResults())
                .extracting(MergeRepoResult::mergeStatus)
                .containsExactly("MERGED", "FAILED");
        assertThat(operator.mergeCalls()).isZero();
        verify(taskProjectMapper, never()).updateMergeResult(any(), any(), any(), any());
        verify(stateMachine, never()).transition(any());
    }

    @Test
    void alreadyCompletedTask_isReplayedWithoutMerge() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("COMPLETED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of(
                project("repo-a", "MERGED", "sha-a"),
                project("repo-b", "MERGED", "sha-b")));

        MergeOutcome outcome = service.merge(command(target("repo-a"), target("repo-b")));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.taskStatus()).isEqualTo("COMPLETED");
        assertThat(operator.mergeCalls()).isZero();
        verify(stateMachine, never()).transition(any());
    }

    @Test
    void alreadyMergedRepo_skippedWhileRemainingRepoMerges() {
        operator.whenMerge("repo-b", GitMergeResult.success("sha-b", ""));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of(
                project("repo-a", "MERGED", "sha-a")));

        MergeOutcome outcome = service.merge(command(target("repo-a"), target("repo-b")));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.repoResults())
                .extracting(MergeRepoResult::commitSha)
                .containsExactly("sha-a", "sha-b");
        assertThat(operator.mergeCalls()).isEqualTo(1);
        verify(taskProjectMapper).updateMergeResult(TASK_ID, "repo-b", "MERGED", "sha-b");
        verify(taskProjectMapper, never()).updateMergeResult(eq(TASK_ID), eq("repo-a"), any(), any());
    }

    @Test
    void emptyTargets_rejected() {
        assertThatThrownBy(() -> service.merge(MergeCommand.of(TASK_ID, KEY, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("合并目标不能为空");
    }

    private AgentTaskTransitionCommand captureTransition() {
        ArgumentCaptor<AgentTaskTransitionCommand> captor =
                ArgumentCaptor.forClass(AgentTaskTransitionCommand.class);
        verify(stateMachine).transition(captor.capture());
        return captor.getValue();
    }

    private static MergeCommand command(MergeTarget... targets) {
        return MergeCommand.of(TASK_ID, KEY, List.of(targets));
    }

    private static MergeTarget target(String repoKey) {
        return MergeTarget.of(repoKey, repo(repoKey), CREDENTIALS,
                new GitMergeRequestRef(42, "https://github.com/org/" + repoKey + "/pull/42"),
                GitMergeOptions.squash());
    }

    private static GitRepository repo(String name) {
        return GitRepository.of(GitPlatform.GITHUB, "github.com", "org", name);
    }

    private static AgentTaskDO task(String status) {
        return AgentTaskDO.builder()
                .id(TASK_ID)
                .taskNo("TASK-20261001-088")
                .status(status)
                .docVersion(3)
                .executionGeneration(1L)
                .build();
    }

    private static AgentTaskProjectDO project(String projectCode, String mergeStatus, String commitHash) {
        return AgentTaskProjectDO.builder()
                .id(1L)
                .taskId(TASK_ID)
                .projectCode(projectCode)
                .baseBranch("main")
                .subDir(projectCode)
                .mergeStatus(mergeStatus)
                .commitHash(commitHash)
                .build();
    }

    private static AgentTaskOperationLogDO operationLog(String action, String from, String to) {
        return AgentTaskOperationLogDO.builder()
                .id(500L)
                .taskId(TASK_ID)
                .action(action)
                .fromStatus(from)
                .toStatus(to)
                .build();
    }

    private static final class RecordingOperator implements GitApiOperator {

        private final GitPlatform platform;
        private final Map<String, GitMergeResult> mergeByRepo = new LinkedHashMap<>();
        private final Map<String, RuntimeException> errorsByRepo = new LinkedHashMap<>();
        private int mergeCount;

        RecordingOperator(GitPlatform platform) {
            this.platform = platform;
        }

        @Override
        public GitPlatform platform() {
            return platform;
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
            return new GitMergeStatus(GitMergeState.OPEN, true, "", "", "feat");
        }

        @Override
        public GitMergeResult merge(GitRepository repository,
                                    GitApiCredentials credentials,
                                    GitMergeRequestRef ref,
                                    GitMergeOptions options) {
            mergeCount++;
            RuntimeException error = errorsByRepo.get(repository.name());
            if (error != null) {
                throw error;
            }
            return mergeByRepo.getOrDefault(repository.name(), GitMergeResult.success("", ref.webUrl()));
        }

        void whenMerge(String repoName, GitMergeResult result) {
            mergeByRepo.put(repoName, result);
        }

        void whenMergeFails(String repoName, RuntimeException error) {
            errorsByRepo.put(repoName, error);
        }

        int mergeCalls() {
            return mergeCount;
        }

    }

}
