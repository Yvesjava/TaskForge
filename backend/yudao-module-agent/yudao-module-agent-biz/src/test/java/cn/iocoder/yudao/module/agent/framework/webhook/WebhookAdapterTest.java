package cn.iocoder.yudao.module.agent.framework.webhook;

import cn.iocoder.yudao.module.agent.framework.notice.NoticeBranchRef;
import cn.iocoder.yudao.module.agent.framework.notice.NoticeCard;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-NOTICE-02 企微/飞书 Webhook 适配器测试。
 *
 * <p>覆盖平台 payload 渲染、平台路由，以及发送、超时与错误重试。发送与重试
 * 逻辑通过假客户端确定性验证，真实 HTTP 路径由本地 {@link MiniHttpServer} 验证。</p>
 */
class WebhookAdapterTest {

    private final WeComWebhookAdapter weComAdapter = new WeComWebhookAdapter();

    private final FeishuWebhookAdapter feishuAdapter = new FeishuWebhookAdapter();

    @Test
    void weComAdapter_rendersStableMarkdownPayload() {
        NoticeCard card = NoticeCard.waitingAcceptance(
                "TASK-20261003-001", "示例任务", "v3",
                List.of(new NoticeBranchRef("backend", "feat/notice", "https://github.com/org/repo.git")),
                List.of("README.md", "src/Main.java"),
                "2 files changed",
                "Tests run: 12, Failures: 0", 1, 15_000L);

        Map<String, Object> payload = weComAdapter.render(card);

        assertThat(payload).containsOnlyKeys("msgtype", "markdown");
        assertThat(payload.get("msgtype")).isEqualTo("markdown");
        Map<String, Object> markdown = (Map<String, Object>) payload.get("markdown");
        assertThat(markdown.keySet()).containsExactly("content");

        String content = (String) markdown.get("content");
        assertThat(content)
                .contains("### 待验收：示例任务")
                .contains("> 任务编号：TASK-20261003-001")
                .contains("> 报告版本：v3")
                .contains("**分支**")
                .contains("- projectCode: backend，branch: feat/notice")
                .contains("**变更文件**")
                .contains("- README.md")
                .contains("**重试次数**")
                .contains("1");
    }

    @Test
    void feishuAdapter_rendersStableInteractiveCard() {
        NoticeCard card = NoticeCard.failed(
                "TASK-20261003-002", "失败示例", "v3",
                "2026-10-03T12:00:00+08:00", "build failed", "tail log", 1, 20_000L);

        Map<String, Object> payload = feishuAdapter.render(card);

        assertThat(payload).containsOnlyKeys("msg_type", "card");
        assertThat(payload.get("msg_type")).isEqualTo("interactive");

        Map<String, Object> cardBody = (Map<String, Object>) payload.get("card");
        Map<String, Object> config = (Map<String, Object>) cardBody.get("config");
        assertThat(config).containsEntry("wide_screen_mode", true);

        Map<String, Object> header = (Map<String, Object>) cardBody.get("header");
        assertThat(header.get("template")).isEqualTo("red");
        Map<String, Object> title = (Map<String, Object>) header.get("title");
        assertThat(title)
                .containsEntry("tag", "plain_text")
                .containsEntry("content", "执行失败：失败示例");

        List<?> elements = (List<?>) cardBody.get("elements");
        assertThat(elements).hasSize(1);
        Map<String, Object> element = (Map<String, Object>) elements.get(0);
        assertThat(element.get("tag")).isEqualTo("div");
        Map<String, Object> text = (Map<String, Object>) element.get("text");
        assertThat(text.get("tag")).isEqualTo("lark_md");

        String content = (String) text.get("content");
        assertThat(content)
                .contains("**执行失败：失败示例**")
                .contains("任务编号：TASK-20261003-002")
                .contains("**失败时间**")
                .contains("2026-10-03T12:00:00+08:00")
                .contains("**错误摘要**")
                .contains("build failed");
    }

    @Test
    void feishuAdapter_mapsHeaderTemplateByEvent() {
        assertThat(headerTemplate(NoticeCard.waitingAcceptance(
                "TASK-1", "t", "v1", List.of(), List.of(), "", "", 0, 0)))
                .isEqualTo("blue");
        assertThat(headerTemplate(NoticeCard.failed(
                "TASK-2", "t", "v1", "now", "e", "log", 0, 0)))
                .isEqualTo("red");
        assertThat(headerTemplate(NoticeCard.mergeConflict(
                "TASK-3", "t", "v1", List.of())))
                .isEqualTo("orange");
    }

    @Test
    void registry_routesByPlatform() {
        WebhookAdapterRegistry registry = new WebhookAdapterRegistry(
                List.of(weComAdapter, feishuAdapter));

        assertThat(registry.get(WebhookPlatform.WECOM)).isSameAs(weComAdapter);
        assertThat(registry.get(WebhookPlatform.FEISHU)).isSameAs(feishuAdapter);
    }

    @Test
    void sender_sendsOnceWhenSuccess() {
        WebhookSender sender = sender(maxRetries(3), fixedClient(WebhookResponse.success(200, "ok")));

        WebhookSendResult result = sender.send(request());

        assertThat(result.attempts()).hasSize(1);
        assertThat(result.retryCount()).isZero();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.firstAttempt().failureReason()).isEmpty();
    }

    @Test
    void sender_retriesOnTimeoutThenSucceeds() {
        WebhookSender sender = sender(maxRetries(3), queuedClient(
                WebhookResponse.timeout(), WebhookResponse.success(200, "ok")));

        WebhookSendResult result = sender.send(request());

        assertThat(result.attempts()).hasSize(2);
        assertThat(result.retryCount()).isEqualTo(1);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.firstAttempt().timedOut()).isTrue();
        assertThat(result.firstAttempt().failureReason()).isEqualTo("超时");
    }

    @Test
    void sender_retriesOnServerErrorThenSucceeds() {
        WebhookSender sender = sender(maxRetries(3), queuedClient(
                WebhookResponse.success(500, "boom"), WebhookResponse.success(200, "ok")));

        WebhookSendResult result = sender.send(request());

        assertThat(result.attempts()).hasSize(2);
        assertThat(result.retryCount()).isEqualTo(1);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.firstAttempt().failureReason()).isEqualTo("HTTP 500");
    }

    @Test
    void sender_stopsAfterRetryBudgetExhausted() {
        WebhookSender sender = sender(maxRetries(2), fixedClient(WebhookResponse.success(503, "busy")));

        WebhookSendResult result = sender.send(request());

        assertThat(result.attempts()).hasSize(3);
        assertThat(result.retryCount()).isEqualTo(2);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isRetryBudgetExhausted()).isTrue();
        assertThat(result.failedAfterRetries()).isTrue();
    }

    @Test
    void sender_doesNotRetryNonRetryableClientError() {
        WebhookSender sender = sender(maxRetries(3), fixedClient(WebhookResponse.success(400, "bad request")));

        WebhookSendResult result = sender.send(request());

        assertThat(result.attempts()).hasSize(1);
        assertThat(result.retryCount()).isZero();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.lastAttempt().failureReason()).isEqualTo("HTTP 400");
    }

    @Test
    void jdkClient_postsJsonAndReturnsStatus() throws Exception {
        try (MiniHttpServer server = new MiniHttpServer(200, "{\"errcode\":0}")) {
            WebhookProperties properties = new WebhookProperties();
            properties.setSecret("test-secret");
            WebhookSignature signature = new WebhookSignature(properties, (nonce, ttl) -> true);
            JdkWebhookClient client = new JdkWebhookClient(properties, signature);

            WebhookResponse response = client.post(WebhookRequest.of(
                    server.url(), Map.of("msgtype", "markdown"), Duration.ofSeconds(5)));

            assertThat(server.awaitHandled()).isTrue();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("errcode");

            MiniHttpServer.Request captured = server.captured();
            assertThat(captured.method()).isEqualTo("POST");
            assertThat(captured.path()).isEqualTo("/webhook");
            assertThat(captured.headers().get("Content-Type")).contains("application/json");
            assertThat(captured.body()).contains("\"msgtype\"").contains("\"markdown\"");

            String timestamp = captured.headers().get("X-TaskForge-Timestamp");
            String nonce = captured.headers().get("X-TaskForge-Nonce");
            String sign = captured.headers().get("X-TaskForge-Signature");
            assertThat(timestamp).isNotBlank();
            assertThat(Math.abs(Long.parseLong(timestamp) - System.currentTimeMillis()))
                    .isLessThan(60_000L);
            assertThat(nonce).isNotBlank();
            assertThat(sign).isEqualTo(hmacSha256Hex(
                    "test-secret", timestamp + "\n" + nonce + "\n" + captured.body()));
        }
    }

    private String headerTemplate(NoticeCard card) {
        Map<?, ?> cardBody = (Map<?, ?>) feishuAdapter.render(card).get("card");
        return (String) ((Map<?, ?>) cardBody.get("header")).get("template");
    }

    private WebhookRequest request() {
        return WebhookRequest.of("https://example.invalid/hook", Map.of("msgtype", "markdown"), Duration.ofSeconds(1));
    }

    private WebhookProperties maxRetries(int maxRetries) {
        WebhookProperties properties = new WebhookProperties();
        properties.setMaxRetries(maxRetries);
        properties.setRetryBackoffMillis(0);
        return properties;
    }

    private WebhookSender sender(WebhookProperties properties, WebhookHttpClient client) {
        return new WebhookSender(client, properties);
    }

    private WebhookHttpClient fixedClient(WebhookResponse response) {
        return request -> response;
    }

    private WebhookHttpClient queuedClient(WebhookResponse... responses) {
        Deque<WebhookResponse> queue = new ArrayDeque<>(List.of(responses));
        return request -> queue.pollFirst();
    }

    private static String hmacSha256Hex(String secret, String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)))
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 一次性本地 HTTP 服务，捕获请求行、头部与正文后返回固定响应。
     */
    private static final class MiniHttpServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final Thread worker;
        private final CountDownLatch handled = new CountDownLatch(1);
        private volatile Request captured;

        MiniHttpServer(int statusCode, String responseBody) throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.worker = new Thread(() -> handle(statusCode, responseBody), "mini-http-server");
            this.worker.setDaemon(true);
            this.worker.start();
        }

        String url() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/webhook";
        }

        boolean awaitHandled() throws InterruptedException {
            return handled.await(10, TimeUnit.SECONDS);
        }

        Request captured() {
            return captured;
        }

        private void handle(int statusCode, String responseBody) {
            try (Socket socket = serverSocket.accept()) {
                captured = readRequest(socket.getInputStream());
                byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
                OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 " + statusCode + " OK\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: " + body.length + "\r\n"
                        + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(body);
                out.flush();
            } catch (IOException ignored) {
                // 测试失败时交由断言暴露
            } finally {
                handled.countDown();
            }
        }

        private Request readRequest(InputStream input) throws IOException {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String path = parts[1];

            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int separator = line.indexOf(':');
                if (separator > 0) {
                    headers.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
                }
            }

            int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
            char[] bodyChars = new char[contentLength];
            int read = 0;
            while (read < contentLength) {
                int count = reader.read(bodyChars, read, contentLength - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
            return new Request(method, path, Map.copyOf(headers), new String(bodyChars, 0, read));
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }

        private record Request(String method, String path, Map<String, String> headers, String body) {
        }
    }

}
