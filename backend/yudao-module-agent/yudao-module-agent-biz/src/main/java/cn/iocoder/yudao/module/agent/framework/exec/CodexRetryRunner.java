package cn.iocoder.yudao.module.agent.framework.exec;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Codex/Claude 自修复重试执行器
 *
 * <p>在 {@link CodexRunner} 之上实现失败后的自动重试：初始执行失败后，最多
 * 重试 {@link AgentExecProperties#getMaxRetries()} 次。每次尝试都复用单次执行
 * 的临时日志采集，因此重试日志、耗时与原因彼此独立；全部尝试通过
 * {@link CodexRetryResult} 归档，供上层写入 {@code execution_log} 与状态回写。
 *
 * <p>超时属于熔断信号：一旦单次尝试被硬超时终止，立即停止后续重试，避免
 * 长时间挂起的僵尸进程占用 Worker 资源。
 *
 * @author TaskForge
 */
@Component
@Slf4j
public class CodexRetryRunner {

    private static final String START_FAILURE_REASON = "启动失败";

    private final CodexRunner runner;

    private final int maxRetries;

    public CodexRetryRunner(CodexRunner runner, AgentExecProperties properties) {
        this.runner = Objects.requireNonNull(runner, "runner 不能为空");
        this.maxRetries = Math.max(0, properties.getMaxRetries());
    }

    /**
     * 使用默认可执行文件执行并应用自修复重试。
     *
     * @param workingDirectory 聚合工作区根目录
     * @param arguments        启动参数（不含可执行文件名）
     * @return 重试归档结果
     */
    public CodexRetryResult run(Path workingDirectory, List<String> arguments) {
        return run(CodexRunRequest.builder()
                .workingDirectory(workingDirectory)
                .arguments(arguments)
                .build());
    }

    /**
     * 启动并等待子进程结束，失败时在预算内自动重试并归档每次尝试。
     *
     * @param request 启动请求
     * @return 重试归档结果
     */
    public CodexRetryResult run(CodexRunRequest request) {
        Objects.requireNonNull(request, "request 不能为空");

        List<CodexRunAttempt> attempts = new ArrayList<>();
        CodexRunAttempt current = executeAttempt(request, 1);
        attempts.add(current);

        while (!current.isSuccess() && !current.timedOut() && attempts.size() - 1 < maxRetries) {
            int attemptNumber = attempts.size() + 1;
            log.info("[CodexRetryRunner] 自修复重试 attempt={}/{} retryCount={} previousReason={} previousDurationMs={}",
                    attemptNumber, maxRetries + 1, attemptNumber - 1,
                    current.retryReason(), current.durationMillis());
            current = executeAttempt(request, attemptNumber);
            attempts.add(current);
        }

        return new CodexRetryResult(attempts, maxRetries);
    }

    private CodexRunAttempt executeAttempt(CodexRunRequest request, int attemptNumber) {
        long started = System.currentTimeMillis();
        try {
            CodexRunResult result = runner.run(request);
            String reason = result.isSuccess() ? "" : reasonOf(result);
            return new CodexRunAttempt(attemptNumber, result, reason);
        } catch (CodexProcessException e) {
            long duration = System.currentTimeMillis() - started;
            CodexRunResult result = new CodexRunResult(
                    e.getExitCode(),
                    e.getPid(),
                    e.getCommandLine(),
                    "",
                    START_FAILURE_REASON,
                    false,
                    duration);
            return new CodexRunAttempt(attemptNumber, result, START_FAILURE_REASON);
        }
    }

    private String reasonOf(CodexRunResult result) {
        if (result.timedOut()) {
            return "超时";
        }
        return "退出码=" + result.exitCode();
    }

}
