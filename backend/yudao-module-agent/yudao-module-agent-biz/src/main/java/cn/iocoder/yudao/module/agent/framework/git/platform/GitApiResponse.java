package cn.iocoder.yudao.module.agent.framework.git.platform;

/**
 * 单次 Git 平台 API 响应。
 *
 * @param statusCode HTTP 状态码；连接失败时为 0
 * @param body       响应体
 * @param error      连接/传输异常信息；成功时为空字符串
 * @author TaskForge
 */
public record GitApiResponse(int statusCode, String body, String error) {

    public GitApiResponse {
        body = body == null ? "" : body;
        error = error == null ? "" : error;
    }

    public static GitApiResponse success(int statusCode, String body) {
        return new GitApiResponse(statusCode, body, "");
    }

    public static GitApiResponse failure(String error) {
        return new GitApiResponse(0, "", error);
    }

    public boolean isSuccess() {
        return error.isEmpty() && statusCode >= 200 && statusCode < 300;
    }

}
