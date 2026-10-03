package cn.iocoder.yudao.module.agent.framework.git.platform;

import cn.iocoder.yudao.module.agent.framework.secret.SecretRedactor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 基于 JDK HttpClient 的 Git 平台 API 传输实现。
 *
 * <p>日志只输出主机名与状态码，避免把请求头中的访问令牌写入日志。</p>
 *
 * @author TaskForge
 */
@Slf4j
@Component
public class JdkGitApiHttpClient implements GitApiHttpClient {

    private final HttpClient httpClient;

    public JdkGitApiHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public GitApiResponse exchange(GitApiRequest request) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.url()))
                    .timeout(Duration.ofSeconds(30));
            request.headers().forEach(builder::header);
            HttpRequest.BodyPublisher publisher = request.body().isEmpty()
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8);
            HttpRequest httpRequest = builder.method(request.method(), publisher).build();
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("[GitApiClient] {} {} -> {}", request.method(), redactUrl(request.url()), response.statusCode());
            return GitApiResponse.success(response.statusCode(), response.body());
        } catch (Exception e) {
            log.warn("[GitApiClient] {} {} 失败: {}", request.method(), redactUrl(request.url()), reasonOf(e));
            return GitApiResponse.failure(reasonOf(e));
        }
    }

    private String redactUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "unknown" : uri.getHost();
            return (uri.getScheme() == null ? "" : uri.getScheme() + "://") + host + "/***";
        } catch (Exception e) {
            return "***";
        }
    }

    private String reasonOf(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return SecretRedactor.redact(message);
    }

}
