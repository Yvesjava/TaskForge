package cn.iocoder.yudao.module.agent.service.merge;

import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiCredentials;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiException;
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
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-MERGE-02 合并前预检测试。
 *
 * <p>验证多仓预检“全有或全无”：任一仓冲突或检查失败时整体未通过，且预检阶段
 * 绝不调用平台合并接口，避免发生部分合并。</p>
 */
class MergePrecheckTest {

    private static final GitApiCredentials CREDENTIALS = GitApiCredentials.of("token");

    @Test
    void allReposMergeable_passesWithoutMerging() {
        RecordingOperator operator = new RecordingOperator(GitPlatform.GITHUB);
        operator.whenQuery("repo-a", mergeable(GitMergeState.OPEN, true));
        operator.whenQuery("repo-b", mergeable(GitMergeState.OPEN, true));
        operator.whenQuery("repo-c", mergeable(GitMergeState.OPEN, true));
        MergePrecheckService service = new MergePrecheckService(registry(operator));

        MergePrecheckResult result = service.precheck(List.of(
                target("repo-a"), target("repo-b"), target("repo-c")));

        assertThat(result.passed()).isTrue();
        assertThat(result.failures()).isEmpty();
        assertThat(result.items()).hasSize(3);
        assertThat(result.items()).allSatisfy(item -> assertThat(item.ref()).isNotNull());
        assertThat(operator.mergeCalls()).isZero();
        assertThat(operator.createCalls()).isEqualTo(3);
        assertThat(operator.queryCalls()).isEqualTo(3);
    }

    @Test
    void anyRepoConflict_failsWholePrecheckAndMergesNothing() {
        RecordingOperator operator = new RecordingOperator(GitPlatform.GITHUB);
        operator.whenQuery("repo-a", mergeable(GitMergeState.OPEN, true));
        operator.whenQuery("repo-b", mergeable(GitMergeState.OPEN, false));
        operator.whenQuery("repo-c", mergeable(GitMergeState.OPEN, true));
        MergePrecheckService service = new MergePrecheckService(registry(operator));

        MergePrecheckResult result = service.precheck(List.of(
                target("repo-a"), target("repo-b"), target("repo-c")));

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).singleElement()
                .satisfies(item -> {
                    assertThat(item.repoKey()).isEqualTo("repo-b");
                    assertThat(item.failure()).isEqualTo(MergePrecheckFailure.CONFLICT);
                });
        // 全有或全无：即使其它仓可合并，也零仓合并
        assertThat(operator.mergeCalls()).isZero();
        // 不因单仓失败提前短路，所有仓仍完成预检
        assertThat(operator.queryCalls()).isEqualTo(3);
    }

    @Test
    void anyRepoQueryError_failsWholePrecheckAndMergesNothing() {
        RecordingOperator operator = new RecordingOperator(GitPlatform.GITHUB);
        operator.whenQuery("repo-a", mergeable(GitMergeState.OPEN, true));
        operator.whenQueryFails("repo-b", new GitApiException(500, "平台暂时不可用"));
        MergePrecheckService service = new MergePrecheckService(registry(operator));

        MergePrecheckResult result = service.precheck(List.of(target("repo-a"), target("repo-b")));

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).singleElement()
                .satisfies(item -> {
                    assertThat(item.repoKey()).isEqualTo("repo-b");
                    assertThat(item.failure()).isEqualTo(MergePrecheckFailure.ERROR);
                    assertThat(item.message()).contains("平台暂时不可用");
                });
        assertThat(operator.mergeCalls()).isZero();
        assertThat(operator.queryCalls()).isEqualTo(2);
    }

    @Test
    void existingRef_queriesWithoutCreating() {
        RecordingOperator operator = new RecordingOperator(GitPlatform.GITHUB);
        operator.whenQuery("repo-a", mergeable(GitMergeState.OPEN, true));
        MergePrecheckService service = new MergePrecheckService(registry(operator));

        GitMergeRequestRef existing = new GitMergeRequestRef(42, "https://github.com/org/repo-a/pull/42");
        MergePrecheckResult result = service.precheck(List.of(
                MergePrecheckTarget.query("repo-a", repo("repo-a"), CREDENTIALS, existing)));

        assertThat(result.passed()).isTrue();
        assertThat(result.items().get(0).ref()).isEqualTo(existing);
        assertThat(operator.createCalls()).isZero();
        assertThat(operator.queryCalls()).isEqualTo(1);
    }

    @Test
    void nullMergeable_treatedAsCheckFailed() {
        RecordingOperator operator = new RecordingOperator(GitPlatform.GITHUB);
        operator.whenQuery("repo-a", new GitMergeStatus(GitMergeState.OPEN, null, "", "", "feat"));
        MergePrecheckService service = new MergePrecheckService(registry(operator));

        MergePrecheckResult result = service.precheck(List.of(target("repo-a")));

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).singleElement()
                .satisfies(item -> assertThat(item.failure()).isEqualTo(MergePrecheckFailure.CHECK_FAILED));
        assertThat(operator.mergeCalls()).isZero();
    }

    @Test
    void alreadyMerged_countsAsPass() {
        RecordingOperator operator = new RecordingOperator(GitPlatform.GITHUB);
        operator.whenQuery("repo-a", new GitMergeStatus(GitMergeState.MERGED, null, "sha", "", "feat"));
        MergePrecheckService service = new MergePrecheckService(registry(operator));

        MergePrecheckResult result = service.precheck(List.of(target("repo-a")));

        assertThat(result.passed()).isTrue();
        assertThat(result.items().get(0).message()).isEqualTo("已合并");
        assertThat(operator.mergeCalls()).isZero();
    }

    @Test
    void emptyTargets_rejected() {
        MergePrecheckService service = new MergePrecheckService(
                registry(new RecordingOperator(GitPlatform.GITHUB)));

        assertThatThrownBy(() -> service.precheck(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    private static MergePrecheckTarget target(String repoKey) {
        return MergePrecheckTarget.create(repoKey, repo(repoKey), CREDENTIALS,
                GitMergeRequest.of("feat-" + repoKey, "", "feature/" + repoKey, "main"));
    }

    private static GitRepository repo(String name) {
        return GitRepository.of(GitPlatform.GITHUB, "github.com", "org", name);
    }

    private static GitMergeStatus mergeable(GitMergeState state, boolean mergeable) {
        return new GitMergeStatus(state, mergeable, "", "", "feat");
    }

    private static GitApiOperatorRegistry registry(GitApiOperator operator) {
        return new GitApiOperatorRegistry(List.of(operator));
    }

    private static final class RecordingOperator implements GitApiOperator {

        private final GitPlatform platform;
        private final Map<String, GitMergeStatus> statusByRepo = new LinkedHashMap<>();
        private final Map<String, RuntimeException> errorsByRepo = new LinkedHashMap<>();
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
            RuntimeException error = errorsByRepo.get(repository.name());
            if (error != null) {
                throw error;
            }
            return statusByRepo.getOrDefault(repository.name(),
                    new GitMergeStatus(GitMergeState.OPEN, true, "", "", "feat"));
        }

        @Override
        public GitMergeResult merge(GitRepository repository,
                                    GitApiCredentials credentials,
                                    GitMergeRequestRef ref,
                                    GitMergeOptions options) {
            mergeCount++;
            return GitMergeResult.success("sha", ref.webUrl());
        }

        void whenQuery(String repoName, GitMergeStatus status) {
            statusByRepo.put(repoName, status);
        }

        void whenQueryFails(String repoName, RuntimeException error) {
            errorsByRepo.put(repoName, error);
        }

        int createCalls() {
            return createCount;
        }

        int queryCalls() {
            return queryCount;
        }

        int mergeCalls() {
            return mergeCount;
        }

    }

}
