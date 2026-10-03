package cn.iocoder.yudao.module.agent.framework.exec;

import java.util.List;

/**
 * Codex/Claude 自修复重试归档结果
 *
 * <p>归档初始尝试与每次重试的独立结果，供上层 Worker 计算重试次数、耗时，
 * 并将最终日志写入 {@code execution_log}。
 *
 * @param attempts  按执行顺序排列的全部尝试
 * @param maxRetries 允许的最大重试次数（不含初始尝试）
 * @author TaskForge
 */
public record CodexRetryResult(List<CodexRunAttempt> attempts, int maxRetries) {

    public CodexRetryResult {
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries 不能为负数");
        }
    }

    /**
     * 实际发生的重试次数，即总尝试数减一。
     */
    public int retryCount() {
        return attempts.isEmpty() ? 0 : attempts.size() - 1;
    }

    /**
     * 首次尝试。
     */
    public CodexRunAttempt firstAttempt() {
        return attempts.isEmpty() ? null : attempts.get(0);
    }

    /**
     * 最后一次尝试（失败时即最终上下文）。
     */
    public CodexRunAttempt lastAttempt() {
        return attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
    }

    /**
     * 最后一次尝试是否成功。
     */
    public boolean isSuccess() {
        CodexRunAttempt last = lastAttempt();
        return last != null && last.isSuccess();
    }

    /**
     * 重试预算是否已耗尽（实际重试次数达到上限）。
     */
    public boolean isRetryBudgetExhausted() {
        return retryCount() >= maxRetries;
    }

    /**
     * 是否在重试预算耗尽后仍未成功。
     */
    public boolean failedAfterRetries() {
        return isRetryBudgetExhausted() && !isSuccess();
    }

    /**
     * 归档总耗时（全部尝试耗时之和）。
     */
    public long totalDurationMillis() {
        return attempts.stream().mapToLong(CodexRunAttempt::durationMillis).sum();
    }

}
