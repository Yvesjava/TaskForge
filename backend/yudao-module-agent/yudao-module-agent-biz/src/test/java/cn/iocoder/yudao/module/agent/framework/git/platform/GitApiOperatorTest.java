package cn.iocoder.yudao.module.agent.framework.git.platform;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-MERGE-01 Git 平台接口抽象测试。
 *
 * <p>覆盖三类平台算子（GitLab/GitHub/Gitea）的请求构造与响应映射、平台路由，以及
 * 通过假适配器验证统一接口可替换、可测试。</p>
 */
class GitApiOperatorTest {

    private static final GitApiCredentials CREDENTIALS = GitApiCredentials.of("token");

    @Test
    void gitLab_create_parsesRefAndBuildsRequest() {
        RecordingClient client = new RecordingClient(GitApiResponse.success(200, """
                {"iid":42,"web_url":"https://gitlab.com/group/sub/demo/-/merge_requests/42"}"""));
        GitLabApiOperator operator = new GitLabApiOperator(client);

        GitMergeRequestRef ref = operator.createMergeRequest(
                repo(GitPlatform.GITLAB), CREDENTIALS,
                GitMergeRequest.of("feat", "desc", "feature/x", "main"));

        assertThat(ref.number()).isEqualTo(42);
        assertThat(ref.webUrl()).isEqualTo("https://gitlab.com/group/sub/demo/-/merge_requests/42");

        GitApiRequest request = client.last();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.url()).isEqualTo("https://gitlab.com/api/v4/projects/group%2Fsub%2Fdemo/merge_requests");
        assertThat(request.headers()).containsEntry("PRIVATE-TOKEN", "token")
                .containsEntry("Content-Type", "application/json");
        JsonNode body = JsonUtils.parseTree(request.body());
        assertThat(body.path("source_branch").asText()).isEqualTo("feature/x");
        assertThat(body.path("target_branch").asText()).isEqualTo("main");
        assertThat(body.path("title").asText()).isEqualTo("feat");
        assertThat(body.path("description").asText()).isEqualTo("desc");
    }

    @Test
    void gitLab_query_mapsStateAndMergeability() {
        RecordingClient client = new RecordingClient(GitApiResponse.success(200, """
                {"state":"opened","merge_status":"can_be_merged","sha":"abc123",
                 "web_url":"https://gitlab.com/group/sub/demo/-/merge_requests/42","title":"feat"}"""));
        GitLabApiOperator operator = new GitLabApiOperator(client);

        GitMergeStatus status = operator.queryMergeRequest(
                repo(GitPlatform.GITLAB), CREDENTIALS, new GitMergeRequestRef(42, ""));

        assertThat(status.state()).isEqualTo(GitMergeState.OPEN);
        assertThat(status.mergeable()).isTrue();
        assertThat(status.commitSha()).isEqualTo("abc123");
        assertThat(status.title()).isEqualTo("feat");
        assertThat(client.last().method()).isEqualTo("GET");
        assertThat(client.last().url()).isEqualTo("https://gitlab.com/api/v4/projects/group%2Fsub%2Fdemo/merge_requests/42");
    }

    @Test
    void gitLab_merge_squashBuildsRequest() {
        RecordingClient client = new RecordingClient(GitApiResponse.success(200, """
                {"state":"merged","merge_commit_sha":"def456","sha":"abc123"}"""));
        GitLabApiOperator operator = new GitLabApiOperator(client);

        GitMergeResult result = operator.merge(
                repo(GitPlatform.GITLAB), CREDENTIALS, new GitMergeRequestRef(42, "web"),
                GitMergeOptions.squash());

        assertThat(result.merged()).isTrue();
        assertThat(result.commitSha()).isEqualTo("def456");
        GitApiRequest request = client.last();
        assertThat(request.method()).isEqualTo("PUT");
        assertThat(request.url()).endsWith("/merge_requests/42/merge");
        JsonNode body = JsonUtils.parseTree(request.body());
        assertThat(body.path("squash").asBoolean()).isTrue();
        assertThat(body.path("should_remove_source_branch").asBoolean()).isTrue();
        assertThat(body.path("merge_when_pipeline_succeeds").asBoolean()).isFalse();
    }

    @Test
    void gitHub_create_parsesRefAndBuildsRequest() {
        RecordingClient client = new RecordingClient(GitApiResponse.success(201, """
                {"number":7,"html_url":"https://github.com/org/demo/pull/7"}"""));
        GitHubApiOperator operator = new GitHubApiOperator(client);

        GitMergeRequestRef ref = operator.createMergeRequest(
                repo(GitPlatform.GITHUB), CREDENTIALS,
                GitMergeRequest.of("feat", "desc", "feature/x", "main"));

        assertThat(ref.number()).isEqualTo(7);
        GitApiRequest request = client.last();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.url()).isEqualTo("https://api.github.com/repos/org/demo/pulls");
        assertThat(request.headers()).containsEntry("Authorization", "Bearer token");
        JsonNode body = JsonUtils.parseTree(request.body());
        assertThat(body.path("head").asText()).isEqualTo("feature/x");
        assertThat(body.path("base").asText()).isEqualTo("main");
        assertThat(body.path("title").asText()).isEqualTo("feat");
    }

    @Test
    void gitHub_query_mapsMergedState() {
        RecordingClient client = new RecordingClient(GitApiResponse.success(200, """
                {"state":"closed","merged":true,"mergeable":null,"merge_commit_sha":"mrg",
                 "html_url":"https://github.com/org/demo/pull/7","title":"feat"}"""));
        GitHubApiOperator operator = new GitHubApiOperator(client);

        GitMergeStatus status = operator.queryMergeRequest(
                repo(GitPlatform.GITHUB), CREDENTIALS, new GitMergeRequestRef(7, ""));

        assertThat(status.state()).isEqualTo(GitMergeState.MERGED);
        assertThat(status.isMerged()).isTrue();
        assertThat(status.mergeable()).isNull();
        assertThat(status.commitSha()).isEqualTo("mrg");
    }

    @Test
    void gitHub_merge_mapsFastForwardToRebase() {
        RecordingClient client = new RecordingClient(GitApiResponse.success(200, """
                {"merged":true,"message":"Pull Request successfully merged","sha":"shasha"}"""));
        GitHubApiOperator operator = new GitHubApiOperator(client);

        GitMergeResult result = operator.merge(
                repo(GitPlatform.GITHUB), CREDENTIALS, new GitMergeRequestRef(7, "web"),
                GitMergeOptions.fastForward());

        assertThat(result.merged()).isTrue();
        assertThat(result.commitSha()).isEqualTo("shasha");
        JsonNode body = JsonUtils.parseTree(client.last().body());
        assertThat(body.path("merge_method").asText()).isEqualTo("rebase");
    }

    @Test
    void gitea_create_parsesRefAndBuildsRequest() {
        RecordingClient client = new RecordingClient(GitApiResponse.success(201, """
                {"number":3,"html_url":"https://gitea.com/org/demo/pulls/3"}"""));
        GiteaApiOperator operator = new GiteaApiOperator(client);

        GitMergeRequestRef ref = operator.createMergeRequest(
                repo(GitPlatform.GITEA), CREDENTIALS,
                GitMergeRequest.of("feat", "desc", "feature/x", "main"));

        assertThat(ref.number()).isEqualTo(3);
        GitApiRequest request = client.last();
        assertThat(request.url()).isEqualTo("https://gitea.com/api/v1/repos/org/demo/pulls");
        assertThat(request.headers()).containsEntry("Authorization", "token token");
    }

    @Test
    void gitea_query_mapsMergeable() {
        RecordingClient client = new RecordingClient(GitApiResponse.success(200, """
                {"state":"open","merged":false,"mergeable":true,"merge_commit_sha":"",
                 "html_url":"https://gitea.com/org/demo/pulls/3","title":"feat"}"""));
        GiteaApiOperator operator = new GiteaApiOperator(client);

        GitMergeStatus status = operator.queryMergeRequest(
                repo(GitPlatform.GITEA), CREDENTIALS, new GitMergeRequestRef(3, ""));

        assertThat(status.state()).isEqualTo(GitMergeState.OPEN);
        assertThat(status.mergeable()).isTrue();
    }

    @Test
    void gitea_merge_fastForwardBuildsDoValue() {
        RecordingClient client = new RecordingClient(GitApiResponse.success(200, """
                {"merged":true,"merge_commit_sha":"ggg"}"""));
        GiteaApiOperator operator = new GiteaApiOperator(client);

        GitMergeResult result = operator.merge(
                repo(GitPlatform.GITEA), CREDENTIALS, new GitMergeRequestRef(3, "web"),
                GitMergeOptions.fastForward());

        assertThat(result.merged()).isTrue();
        assertThat(result.commitSha()).isEqualTo("ggg");
        JsonNode body = JsonUtils.parseTree(client.last().body());
        assertThat(body.path("Do").asText()).isEqualTo("fast-forward-only");
    }

    @Test
    void registry_routesByPlatform() {
        GitApiHttpClient noop = request -> GitApiResponse.success(200, "{}");
        GitLabApiOperator gitlab = new GitLabApiOperator(noop);
        GitHubApiOperator github = new GitHubApiOperator(noop);
        GiteaApiOperator gitea = new GiteaApiOperator(noop);

        GitApiOperatorRegistry registry = new GitApiOperatorRegistry(List.of(gitlab, github, gitea));

        assertThat(registry.get(GitPlatform.GITLAB)).isSameAs(gitlab);
        assertThat(registry.get(GitPlatform.GITHUB)).isSameAs(github);
        assertThat(registry.get(GitPlatform.GITEA)).isSameAs(gitea);
    }

    @Test
    void merge_conflict_returnsFailureResult() {
        RecordingClient client = new RecordingClient(GitApiResponse.success(409, """
                {"message":"Pull Request is not mergeable"}"""));
        GitHubApiOperator operator = new GitHubApiOperator(client);

        GitMergeResult result = operator.merge(
                repo(GitPlatform.GITHUB), CREDENTIALS, new GitMergeRequestRef(7, "web"),
                GitMergeOptions.squash());

        assertThat(result.merged()).isFalse();
        assertThat(result.state()).isEqualTo(GitMergeState.UNKNOWN);
        assertThat(result.message()).contains("not mergeable");
    }

    @Test
    void create_httpError_throwsGitApiException() {
        RecordingClient client = new RecordingClient(GitApiResponse.success(422, """
                {"message":"Branch already exists"}"""));
        GitLabApiOperator operator = new GitLabApiOperator(client);

        assertThatThrownBy(() -> operator.createMergeRequest(
                repo(GitPlatform.GITLAB), CREDENTIALS,
                GitMergeRequest.of("feat", "", "feature/x", "main")))
                .isInstanceOf(GitApiException.class)
                .extracting(e -> ((GitApiException) e).getStatusCode())
                .isEqualTo(422);
    }

    @Test
    void fakeAdapter_supportsUnifiedInterface() {
        FakeGitApiOperator fake = new FakeGitApiOperator(GitPlatform.GITHUB);
        GitRepository repository = repo(GitPlatform.GITHUB);

        GitMergeRequestRef ref = fake.createMergeRequest(repository, CREDENTIALS,
                GitMergeRequest.of("feat", "", "feature/x", "main"));
        GitMergeStatus status = fake.queryMergeRequest(repository, CREDENTIALS, ref);
        GitMergeResult result = fake.merge(repository, CREDENTIALS, ref, GitMergeOptions.squash());

        assertThat(ref.number()).isEqualTo(1);
        assertThat(status.state()).isEqualTo(GitMergeState.MERGED);
        assertThat(status.isMerged()).isTrue();
        assertThat(result.merged()).isTrue();
        assertThat(result.commitSha()).isEqualTo("fake-sha");
    }

    private GitRepository repo(GitPlatform platform) {
        return switch (platform) {
            case GITLAB -> GitRepository.of(GitPlatform.GITLAB, "gitlab.com", "group/sub", "demo");
            case GITHUB -> GitRepository.of(GitPlatform.GITHUB, "github.com", "org", "demo");
            case GITEA -> GitRepository.of(GitPlatform.GITEA, "gitea.com", "org", "demo");
        };
    }

    private static final class FakeGitApiOperator implements GitApiOperator {

        private final GitPlatform platform;

        FakeGitApiOperator(GitPlatform platform) {
            this.platform = platform;
        }

        @Override
        public GitPlatform platform() {
            return platform;
        }

        @Override
        public GitMergeRequestRef createMergeRequest(GitRepository repository, GitApiCredentials credentials, GitMergeRequest request) {
            return new GitMergeRequestRef(1, "");
        }

        @Override
        public GitMergeStatus queryMergeRequest(GitRepository repository, GitApiCredentials credentials, GitMergeRequestRef ref) {
            return new GitMergeStatus(GitMergeState.MERGED, true, "fake-sha", "", "feat");
        }

        @Override
        public GitMergeResult merge(GitRepository repository, GitApiCredentials credentials, GitMergeRequestRef ref, GitMergeOptions options) {
            return GitMergeResult.success("fake-sha", "");
        }

    }

    private static final class RecordingClient implements GitApiHttpClient {

        private final List<GitApiRequest> requests = new ArrayList<>();
        private final Deque<GitApiResponse> responses = new ArrayDeque<>();

        RecordingClient(GitApiResponse... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public GitApiResponse exchange(GitApiRequest request) {
            requests.add(request);
            GitApiResponse response = responses.pollFirst();
            return response == null ? GitApiResponse.success(200, "{}") : response;
        }

        GitApiRequest last() {
            return requests.get(requests.size() - 1);
        }

    }

}
