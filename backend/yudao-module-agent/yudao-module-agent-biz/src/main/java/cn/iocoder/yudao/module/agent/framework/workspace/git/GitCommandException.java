package cn.iocoder.yudao.module.agent.framework.workspace.git;

/**
 * Git 命令执行异常
 *
 * <p>保留退出码与截断后的输出，供上层归一化为业务错误码。
 * 该类不保存原始命令参数，避免把可能含凭证的仓库地址写入日志。
 *
 * @author TaskForge
 */
public class GitCommandException extends RuntimeException {

    /**
     * 最大输出长度，避免把巨型日志塞进异常消息
     */
    private static final int MAX_OUTPUT_LENGTH = 4000;

    private final int exitCode;

    private final String output;

    public GitCommandException(String message, int exitCode, String output) {
        super(message);
        this.exitCode = exitCode;
        this.output = output;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getOutput() {
        return output;
    }

    /**
     * 获取可安全写入错误消息的输出摘要，超长时保留头尾。
     *
     * @return 截断后的输出文本
     */
    public String getSanitizedOutput() {
        if (output == null || output.isBlank()) {
            return "";
        }
        String value = output.trim();
        if (value.length() <= MAX_OUTPUT_LENGTH) {
            return value;
        }
        int keep = MAX_OUTPUT_LENGTH / 2;
        return value.substring(0, keep)
                + "\n...[truncated, " + value.length() + " chars]...\n"
                + value.substring(value.length() - keep);
    }

}
