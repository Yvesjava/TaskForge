package cn.iocoder.yudao.module.agent.framework.git.platform;

/**
 * Git 平台 API 调用异常。
 *
 * @author TaskForge
 */
public class GitApiException extends RuntimeException {

    private final int statusCode;

    public GitApiException(String message) {
        this(0, message);
    }

    public GitApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

}
