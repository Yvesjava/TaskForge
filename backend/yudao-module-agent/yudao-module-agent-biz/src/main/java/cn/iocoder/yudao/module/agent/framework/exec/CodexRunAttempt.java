package cn.iocoder.yudao.module.agent.framework.exec;

import java.util.List;
import java.util.Objects;

/**
 * 单次 Codex/Claude 执行尝试
 *
 * <p>自修复重试归档的基本单元：每次尝试都独立保留退出码、PID、启动参数、
 * stdout/stderr、超时标记与耗时，并记录本次尝试失败后触发重试的原因。
 *
 * @param attemptNumber 尝试序号，从 1 开始
 * @param result        单次执行结果（始终非空）
 * @param retryReason   本次尝试失败并触发重试的原因；成功时为空字符串
 * @author TaskForge
 */
public record CodexRunAttempt(
        int attemptNumber,
        CodexRunResult result,
        String retryReason) {

    public CodexRunAttempt {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber 必须从 1 开始");
        }
        Objects.requireNonNull(result, "result 不能为空");
        retryReason = retryReason == null ? "" : retryReason;
    }

    public boolean isSuccess() {
        return result.isSuccess();
    }

    public boolean isRetry() {
        return attemptNumber > 1;
    }

    public int exitCode() {
        return result.exitCode();
    }

    public long pid() {
        return result.pid();
    }

    public List<String> commandLine() {
        return result.commandLine();
    }

    public String stdout() {
        return result.stdout();
    }

    public String stderr() {
        return result.stderr();
    }

    public boolean timedOut() {
        return result.timedOut();
    }

    public long durationMillis() {
        return result.durationMillis();
    }

}
