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
 * TASK-MERGE-04 冲突转人工与继续合并测试。
 *
 * <p>覆盖预检冲突阻断全部仓合并、冲突解决后的重试恢复、继续合并跳过已合并仓库，
 * 以及重复回调的幂等重放。</p>
 */
@ExtendWith(MockitoExtension.class)
class MergeConflictTest {

    private static final Long TASK_ID = 9012L;
    private static final String KEY = "merge_conflict_20261003_0001";
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
    private MergeConflictService service;

    @BeforeEach
    void setUp() {
        operator = new RecordingOperator(GitPlatform.GITHUB);
        GitApiOperatorRegistry registry = new GitApiOperatorRegistry(List.of(operator));
        MergePrecheckService precheckService = new MergePrecheckService(registry);
        MergeService mergeService = new MergeService(registry, taskMapper, taskProjectMapper,
                operationLogMapper, stateMachine);
        service = new MergeConflictService(precheckService, mergeService,
                taskMapper, taskProjectMapper, operationLogMapper, stateMachine);
    }

    @Test
    void execute_conflictDuringPrecheck_marksManualAndMergesNothing() {
        operator.whenQuery("repo-a", mergeable(true));
        operator.whenQuery("repo-b", mergeable(false));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED"));
        when(stateMachine.transition(any())).thenReturn(AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL);

        MergeOutcome outcome = service.execute(command(target("repo-a"), target("repo-b")));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.taskStatus()).isEqualTo("MERGE_CONFLICT_PENDING_MANUAL");
        assertThat(outcome.repoResults())
                .extracting(MergeRepoResult::mergeStatus)
                .containsExactly("UNMERGED", "CONFLICT");
        assertThat(operator.mergeCalls()).isZero();

        AgentTaskTransitionCommand transition = captureTransition();
        assertThat(transition.getAction()).isEqualTo(AgentTaskAction.MERGE_CONFLICT);
        assertThat(transition.getFromStatus()).isEqualTo(AgentTaskStatus.ACCEPTED);
        assertThat(transition.getRequestIdempotencyKey()).isEqualTo(KEY);

        verify(taskProjectMapper).updateMergeResult(TASK_ID, "repo-b", "CONFLICT", "");
        verify(taskProjectMapper, never()).updateMergeResult(eq(TASK_ID), eq("repo-a"), any(), any());
    }

    @Test
    void execute_allPass_mergesAllRepos() {
        operator.whenQuery("repo-a", mergeable(true));
        operator.whenQuery("repo-b", mergeable(true));
        operator.whenMerge("repo-a", GitMergeResult.success("sha-a", "https://github.com/org/repo-a/pull/1"));
        operator.whenMerge("repo-b", GitMergeResult.success("sha-b", "https://github.com/org/repo-b/pull/1"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of());

        MergeOutcome outcome = service.execute(command(target("repo-a"), target("repo-b")));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.taskStatus()).isEqualTo("ACCEPTED");
        assertThat(operator.mergeCalls()).isEqualTo(2);
        verify(taskProjectMapper).updateMergeResult(TASK_ID, "repo-a", "MERGED", "sha-a");
        verify(taskProjectMapper).updateMergeResult(TASK_ID, "repo-b", "MERGED", "sha-b");
        verify(stateMachine, never()).transition(any());
    }

    @Test
    void retry_conflictResolved_recoversAndContinuesMerge() {
        operator.whenQuery("repo-a", mergeable(true));
        operator.whenQuery("repo-b", mergeable(true));
        operator.whenMerge("repo-a", GitMergeResult.success("sha-a", ""));
        operator.whenMerge("repo-b", GitMergeResult.success("sha-b", ""));
        when(taskMapper.selectById(TASK_ID))
                .thenReturn(task("MERGE_CONFLICT_PENDING_MANUAL"), task("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID))
                .thenReturn(List.of(project("repo-b", "CONFLICT", "")));
        when(stateMachine.transition(any())).thenReturn(AgentTaskStatus.ACCEPTED);

        MergeOutcome outcome = service.retry(command(target("repo-a"), target("repo-b")));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.taskStatus()).isEqualTo("ACCEPTED");
        assertThat(operator.mergeCalls()).isEqualTo(2);

        AgentTaskTransitionCommand transition = captureTransition();
        assertThat(transition.getAction()).isEqualTo(AgentTaskAction.MERGE_RETRY);
        assertThat(transition.getFromStatus()).isEqualTo(AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL);
        assertThat(transition.getRequestIdempotencyKey()).isNull();
    }

    @Test
    void retry_stillConflict_staysManualAndMergesNothing() {
        operator.whenQuery("repo-a", mergeable(true));
        operator.whenQuery("repo-b", mergeable(false));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("MERGE_CONFLICT_PENDING_MANUAL"));

        MergeOutcome outcome = service.retry(command(target("repo-a"), target("repo-b")));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.taskStatus()).isEqualTo("MERGE_CONFLICT_PENDING_MANUAL");
        assertThat(operator.mergeCalls()).isZero();
        verify(stateMachine, never()).transition(any());
        verify(taskProjectMapper).updateMergeResult(TASK_ID, "repo-b", "CONFLICT", "");
    }

    @Test
    void retry_alreadyMergedRepo_skippedOnContinue() {
        operator.whenQuery("repo-a", mergeable(true));
        operator.whenQuery("repo-b", mergeable(true));
        operator.whenMerge("repo-b", GitMergeResult.success("sha-b", ""));
        when(taskMapper.selectById(TASK_ID))
                .thenReturn(task("MERGE_CONFLICT_PENDING_MANUAL"), task("ACCEPTED"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID))
                .thenReturn(List.of(project("repo-a", "MERGED", "sha-a")));
        when(stateMachine.transition(any())).thenReturn(AgentTaskStatus.ACCEPTED);

        MergeOutcome outcome = service.retry(command(target("repo-a"), target("repo-b")));

        assertThat(outcome.success()).isTrue();
        assertThat(operator.mergeCalls()).isEqualTo(1);
        verify(taskProjectMapper).updateMergeResult(TASK_ID, "repo-b", "MERGED", "sha-b");
        verify(taskProjectMapper, never()).updateMergeResult(eq(TASK_ID), eq("repo-a"), any(), any());
    }

    @Test
    void execute_duplicateConflict_isIdempotent() {
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, KEY))
                .thenReturn(operationLog("MERGE_CONFLICT", "ACCEPTED", "MERGE_CONFLICT_PENDING_MANUAL"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("MERGE_CONFLICT_PENDING_MANUAL"));
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of(
                project("repo-a", "UNMERGED", ""),
                project("repo-b", "CONFLICT", "")));

        MergeOutcome outcome = service.execute(command(target("repo-a"), target("repo-b")));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.taskStatus()).isEqualTo("MERGE_CONFLICT_PENDING_MANUAL");
        assertThat(operator.mergeCalls()).isZero();
        assertThat(operator.queryCalls()).isZero();
        verify(stateMachine, never()).transition(any());
    }

    @Test
    void command_emptyTargets_rejected() {
        assertThatThrownBy(() -> MergeConflictCommand.of(TASK_ID, KEY, List.of(), GitMergeOptions.squash()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("合并预检目标不能为空");
    }

    private AgentTaskTransitionCommand captureTransition() {
        ArgumentCaptor<AgentTaskTransitionCommand> captor =
                ArgumentCaptor.forClass(AgentTaskTransitionCommand.class);
        verify(stateMachine).transition(captor.capture());
        return captor.getValue();
    }

    private static MergeConflictCommand command(MergePrecheckTarget... targets) {
        return MergeConflictCommand.of(TASK_ID, KEY, List.of(targets), GitMergeOptions.squash());
    }

    private static MergePrecheckTarget target(String repoKey) {
        return MergePrecheckTarget.create(repoKey, repo(repoKey), CREDENTIALS,
                GitMergeRequest.of("feat-" + repoKey, "", "feature/" + repoKey, "main"));
    }

    private static GitRepository repo(String name) {
        return GitRepository.of(GitPlatform.GITHUB, "github.com", "org", name);
    }

    private static GitMergeStatus mergeable(boolean mergeable) {
        return new GitMergeStatus(GitMergeState.OPEN, mergeable, "", "", "feat");
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
        private final Map<String, GitMergeStatus> statusByRepo = new LinkedHashMap<>();
        private final Map<String, GitMergeResult> mergeByRepo = new LinkedHashMap<>();
        private int createCount;
        private int queryCount;
        private int mergeCount;
        private long nextNumber = 100;

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
            createCount++;
            return new GitMergeRequestRef(nextNumber++, "");
        }

        @Override
        public GitMergeStatus queryMergeRequest(GitRepository repository,
                                                GitApiCredentials credentials,
                                                GitMergeRequestRef ref) {
            queryCount++;
            return statusByRepo.getOrDefault(repository.name(), mergeable(true));
        }

        @Override
        public GitMergeResult merge(GitRepository repository,
                                    GitApiCredentials credentials,
                                    GitMergeRequestRef ref,
                                    GitMergeOptions options) {
            mergeCount++;
            return mergeByRepo.getOrDefault(repository.name(), GitMergeResult.success("", ref.webUrl()));
        }

        void whenQuery(String repoName, GitMergeStatus status) {
            statusByRepo.put(repoName, status);
        }

        void whenMerge(String repoName, GitMergeResult result) {
            mergeByRepo.put(repoName, result);
        }

        int mergeCalls() {
            return mergeCount;
        }

        int queryCalls() {
            return queryCount;
        }

    }

}
