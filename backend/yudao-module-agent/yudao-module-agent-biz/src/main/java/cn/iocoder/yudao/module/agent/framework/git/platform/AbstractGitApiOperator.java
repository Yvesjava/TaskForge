package cn.iocoder.yudao.module.agent.framework.git.platform;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Git 平台算子公共基类。
 *
 * <p>承载 HTTP 传输、2xx 校验、错误信息提取与路径编码等公共逻辑，子类只负责
 * 平台特有的鉴权头、端点、请求体与响应字段映射。</p>
 *
 * @author TaskForge
 */
public abstract class AbstractGitApiOperator implements GitApiOperator {

    protected final GitApiHttpClient httpClient;

    protected AbstractGitApiOperator(GitApiHttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient 不能为空");
    }

    protected final GitApiResponse exchange(GitApiRequest request) {
        return httpClient.exchange(request);
    }

    protected final GitApiResponse requireSuccess(GitApiRequest request) {
        GitApiResponse response = exchange(request);
        if (response.isSuccess()) {
            return response;
        }
        throw toException(response);
    }

    protected final GitApiException toException(GitApiResponse response) {
        if (response.statusCode() == 0) {
            return new GitApiException(response.error().isEmpty() ? "Git 平台请求失败" : response.error());
        }
        return new GitApiException(response.statusCode(), errorMessage(response));
    }

    protected final Map<String, String> requestHeaders(GitApiCredentials credentials) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.putAll(authHeaders(credentials));
        return headers;
    }

    protected abstract Map<String, String> authHeaders(GitApiCredentials credentials);

    protected static JsonNode requireJson(String body) {
        if (body == null || body.isBlank()) {
            return JsonUtils.parseTree("{}");
        }
        return JsonUtils.parseTree(body);
    }

    protected static String errorMessage(GitApiResponse response) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return "HTTP " + response.statusCode();
        }
        try {
            JsonNode node = JsonUtils.parseTree(body);
            JsonNode message = node.get("message");
            if (message != null && !message.isNull() && !message.asText().isBlank()) {
                return message.asText();
            }
            JsonNode error = node.get("error");
            if (error != null && !error.isNull() && !error.asText().isBlank()) {
                return error.asText();
            }
        } catch (RuntimeException ignored) {
            // 非 JSON 响应体时回退到原始文本
        }
        return "HTTP " + response.statusCode() + ": " + body;
    }

    protected static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    protected static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

}
