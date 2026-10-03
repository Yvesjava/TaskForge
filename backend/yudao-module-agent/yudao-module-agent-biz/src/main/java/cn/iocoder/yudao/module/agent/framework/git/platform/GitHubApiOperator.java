package cn.iocoder.yudao.module.agent.framework.git.platform;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GitHub 平台算子。
 *
 * <p>Fast-Forward 在 GitHub 上没有独立合并方式，按线性历史近似映射为 rebase。</p>
 *
 * @author TaskForge
 */
@Component
public class GitHubApiOperator extends AbstractGitApiOperator {

    public GitHubApiOperator(GitApiHttpClient httpClient) {
        super(httpClient);
    }

    @Override
    public GitPlatform platform() {
        return GitPlatform.GITHUB;
    }

    @Override
    protected Map<String, String> authHeaders(GitApiCredentials credentials) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + credentials.token());
        return headers;
    }

    @Override
    public GitMergeRequestRef createMergeRequest(GitRepository repository, GitApiCredentials credentials, GitMergeRequest request) {
        String url = repository.baseUrl() + "/repos/" + encodePathSegment(repository.owner())
                + "/" + encodePathSegment(repository.name()) + "/pulls";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", request.title());
        body.put("head", request.sourceBranch());
        body.put("base", request.targetBranch());
        body.put("body", request.description());
        GitApiResponse response = requireSuccess(GitApiRequest.of(
                "POST", url, requestHeaders(credentials), JsonUtils.toJsonString(body)));
        JsonNode node = requireJson(response.body());
        return new GitMergeRequestRef(node.path("number").asLong(), node.path("html_url").asText());
    }

    @Override
    public GitMergeStatus queryMergeRequest(GitRepository repository, GitApiCredentials credentials, GitMergeRequestRef ref) {
        String url = repository.baseUrl() + "/repos/" + encodePathSegment(repository.owner())
                + "/" + encodePathSegment(repository.name()) + "/pulls/" + ref.number();
        GitApiResponse response = requireSuccess(GitApiRequest.of(
                "GET", url, requestHeaders(credentials), ""));
        return toStatus(requireJson(response.body()));
    }

    @Override
    public GitMergeResult merge(GitRepository repository, GitApiCredentials credentials, GitMergeRequestRef ref, GitMergeOptions options) {
        String url = repository.baseUrl() + "/repos/" + encodePathSegment(repository.owner())
                + "/" + encodePathSegment(repository.name()) + "/pulls/" + ref.number() + "/merge";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merge_method", mergeMethod(options.strategy()));
        GitApiResponse response = exchange(GitApiRequest.of(
                "PUT", url, requestHeaders(credentials), JsonUtils.toJsonString(body)));
        if (response.isSuccess()) {
            JsonNode node = requireJson(response.body());
            if (node.path("merged").asBoolean(false)) {
                return GitMergeResult.success(node.path("sha").asText(), ref.webUrl());
            }
            return GitMergeResult.failure(GitMergeState.UNKNOWN, ref.webUrl(), node.path("message").asText());
        }
        return GitMergeResult.failure(GitMergeState.UNKNOWN, ref.webUrl(), errorMessage(response));
    }

    private GitMergeStatus toStatus(JsonNode node) {
        boolean merged = node.path("merged").asBoolean(false);
        GitMergeState state;
        if (merged) {
            state = GitMergeState.MERGED;
        } else {
            state = switch (node.path("state").asText()) {
                case "open" -> GitMergeState.OPEN;
                case "closed" -> GitMergeState.CLOSED;
                default -> GitMergeState.UNKNOWN;
            };
        }
        Boolean mergeable = node.hasNonNull("mergeable") ? node.path("mergeable").asBoolean() : null;
        return new GitMergeStatus(state, mergeable, node.path("merge_commit_sha").asText(),
                node.path("html_url").asText(), node.path("title").asText());
    }

    private String mergeMethod(GitMergeStrategy strategy) {
        return switch (strategy) {
            case MERGE -> "merge";
            case SQUASH -> "squash";
            case REBASE, FAST_FORWARD -> "rebase";
        };
    }

}
