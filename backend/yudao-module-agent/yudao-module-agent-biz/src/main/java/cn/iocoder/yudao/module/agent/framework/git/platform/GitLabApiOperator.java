package cn.iocoder.yudao.module.agent.framework.git.platform;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GitLab 平台算子。
 *
 * <p>仓库路径使用 URL 编码后的完整项目路径（支持子组）。GitLab 的 Fast-Forward 由
 * 项目/MR 设置控制，合并接口按 {@code squash} 参数区分 Squash 与普通合并。</p>
 *
 * @author TaskForge
 */
@Component
public class GitLabApiOperator extends AbstractGitApiOperator {

    public GitLabApiOperator(GitApiHttpClient httpClient) {
        super(httpClient);
    }

    @Override
    public GitPlatform platform() {
        return GitPlatform.GITLAB;
    }

    @Override
    protected Map<String, String> authHeaders(GitApiCredentials credentials) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("PRIVATE-TOKEN", credentials.token());
        return headers;
    }

    @Override
    public GitMergeRequestRef createMergeRequest(GitRepository repository, GitApiCredentials credentials, GitMergeRequest request) {
        String url = repository.baseUrl() + "/projects/" + encodePathSegment(repository.path()) + "/merge_requests";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source_branch", request.sourceBranch());
        body.put("target_branch", request.targetBranch());
        body.put("title", request.title());
        body.put("description", request.description());
        GitApiResponse response = requireSuccess(GitApiRequest.of(
                "POST", url, requestHeaders(credentials), JsonUtils.toJsonString(body)));
        JsonNode node = requireJson(response.body());
        return new GitMergeRequestRef(node.path("iid").asLong(), node.path("web_url").asText());
    }

    @Override
    public GitMergeStatus queryMergeRequest(GitRepository repository, GitApiCredentials credentials, GitMergeRequestRef ref) {
        String url = repository.baseUrl() + "/projects/" + encodePathSegment(repository.path())
                + "/merge_requests/" + ref.number();
        GitApiResponse response = requireSuccess(GitApiRequest.of(
                "GET", url, requestHeaders(credentials), ""));
        return toStatus(requireJson(response.body()));
    }

    @Override
    public GitMergeResult merge(GitRepository repository, GitApiCredentials credentials, GitMergeRequestRef ref, GitMergeOptions options) {
        String url = repository.baseUrl() + "/projects/" + encodePathSegment(repository.path())
                + "/merge_requests/" + ref.number() + "/merge";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("should_remove_source_branch", options.removeSourceBranch());
        body.put("squash", options.strategy() == GitMergeStrategy.SQUASH);
        body.put("merge_when_pipeline_succeeds", false);
        GitApiResponse response = exchange(GitApiRequest.of(
                "PUT", url, requestHeaders(credentials), JsonUtils.toJsonString(body)));
        if (response.isSuccess()) {
            JsonNode node = requireJson(response.body());
            String sha = firstNonBlank(node.path("merge_commit_sha").asText(), node.path("sha").asText());
            return GitMergeResult.success(sha, ref.webUrl());
        }
        return GitMergeResult.failure(GitMergeState.UNKNOWN, ref.webUrl(), errorMessage(response));
    }

    private GitMergeStatus toStatus(JsonNode node) {
        String state = node.path("state").asText();
        GitMergeState mapped = switch (state) {
            case "opened" -> GitMergeState.OPEN;
            case "merged" -> GitMergeState.MERGED;
            case "closed" -> GitMergeState.CLOSED;
            case "locked" -> GitMergeState.LOCKED;
            default -> GitMergeState.UNKNOWN;
        };
        String mergeStatus = node.path("merge_status").asText();
        Boolean mergeable = "can_be_merged".equals(mergeStatus) ? Boolean.TRUE
                : "cannot_be_merged".equals(mergeStatus) ? Boolean.FALSE : null;
        String sha = firstNonBlank(node.path("merge_commit_sha").asText(), node.path("sha").asText());
        return new GitMergeStatus(mapped, mergeable, sha, node.path("web_url").asText(), node.path("title").asText());
    }

}
