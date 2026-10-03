package cn.iocoder.yudao.module.agent.framework.webhook;

/**
 * 单次 HTTP 投递响应
 *
 * <p>除 HTTP 状态码与响应体外，还记录是否超时与连接/异常错误信息，供上层
 * 判断本次投递是否成功、是否值得重试。</p>
 *
 * @param statusCode HTTP 状态码；连接失败或超时时为 0
 * @param body       响应体
 * @param timedOut   是否因超时被终止
 * @param error      连接/序列化等异常信息；成功时为空字符串
 * @author TaskForge
 */
public record WebhookResponse(int statusCode, String body, boolean timedOut, String error) {

    public WebhookResponse {
        body = body == null ? "" : body;
        error = error == null ? "" : error;
    }

    public static WebhookResponse success(int statusCode, String body) {
        return new WebhookResponse(statusCode, body, false, "");
    }

    public static WebhookResponse timeout() {
        return new WebhookResponse(0, "", true, "");
    }

    public static WebhookResponse failure(int statusCode, String body, String error) {
        return new WebhookResponse(statusCode, body, false, error);
    }

    /**
     * 本次投递是否成功：未超时、无异常且返回 2xx。
     */
    public boolean isSuccess() {
        return !timedOut && error.isEmpty() && statusCode >= 200 && statusCode < 300;
    }

    /**
     * 本次投递是否值得重试：超时、连接异常（状态码为 0）或服务端 5xx 属于瞬时失败。
     * 4xx 表示请求本身不被接收，重试通常无效。
     */
    public boolean isRetryable() {
        if (timedOut) {
            return true;
        }
        if (statusCode == 0) {
            return true;
        }
        return statusCode >= 500;
    }

    public String failureReason() {
        if (timedOut) {
            return "超时";
        }
        if (statusCode == 0) {
            return error.isEmpty() ? "连接失败" : error;
        }
        return "HTTP " + statusCode;
    }

}
