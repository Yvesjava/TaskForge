package cn.iocoder.yudao.module.agent.framework.exec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CodexRetryRunner} 的自修复重试与归档契约测试
 *
 * <p>使用真实 JVM 子进程充当假 Codex，验证初始失败后最多重试两次，且每次
 * 尝试都有独立的 stdout/stderr、耗时与重试原因。
 */
class CodexRetryTest {

    @TempDir
    Path tempDir;

    private final AgentExecProperties properties = new AgentExecProperties();

    private final CodexRunner runner = new CodexRunner(properties);

    private final CodexRetryRunner retryRunner = new CodexRetryRunner(runner, properties);

    @Test
    void run_archivesEveryAttemptWithIndependentLogsDurationAndReason() throws Exception {
        Path stateFile = tempDir.resolve("retry-state.txt");

        CodexRetryResult result = retryRunner.run(request(stateFile, 2, 0));

        assertThat(result.attempts()).hasSize(3);
        assertThat(result.retryCount()).isEqualTo(2);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isRetryBudgetExhausted()).isTrue();
        assertThat(result.failedAfterRetries()).isFalse();

        for (CodexRunAttempt attempt : result.attempts()) {
            assertThat(attempt.stdout()).contains("CODX-RETRY-STDOUT-" + attempt.attemptNumber());
            assertThat(attempt.stderr()).contains("CODX-RETRY-STDERR-" + attempt.attemptNumber());
            assertThat(attempt.durationMillis()).isGreaterThanOrEqualTo(0L);
        }

        assertThat(result.attempts().get(0).retryReason()).isEqualTo("退出码=3");
        assertThat(result.attempts().get(1).retryReason()).isEqualTo("退出码=3");
        assertThat(result.attempts().get(2).retryReason()).isEmpty();
    }

    @Test
    void run_stopsAfterTwoRetriesWhenEveryAttemptFails() throws Exception {
        Path stateFile = tempDir.resolve("retry-state-always-fail.txt");

        CodexRetryResult result = retryRunner.run(request(stateFile, 99, 0));

        assertThat(result.attempts()).hasSize(3);
        assertThat(result.retryCount()).isEqualTo(2);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isRetryBudgetExhausted()).isTrue();
        assertThat(result.failedAfterRetries()).isTrue();
        assertThat(result.lastAttempt()).isSameAs(result.attempts().get(2));
        assertThat(result.totalDurationMillis()).isGreaterThanOrEqualTo(0L);

        for (CodexRunAttempt attempt : result.attempts()) {
            assertThat(attempt.exitCode()).isEqualTo(3);
            assertThat(attempt.retryReason()).isEqualTo("退出码=3");
            assertThat(attempt.stdout()).contains("CODX-RETRY-STDOUT-" + attempt.attemptNumber());
        }
    }

    @Test
    void run_doesNotRetryWhenFirstAttemptSucceeds() throws Exception {
        Path stateFile = tempDir.resolve("retry-state-first-success.txt");

        CodexRetryResult result = retryRunner.run(request(stateFile, 0, 0));

        assertThat(result.attempts()).hasSize(1);
        assertThat(result.retryCount()).isZero();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.firstAttempt().retryReason()).isEmpty();
        assertThat(result.firstAttempt().stdout()).contains("CODX-RETRY-STDOUT-1");
    }

    private CodexRunRequest request(Path stateFile, int failCount, int successExitCode) {
        return CodexRunRequest.builder()
                .workingDirectory(tempDir)
                .executable(javaExecutable())
                .arguments(List.of("-cp", System.getProperty("java.class.path"),
                        CodexRunnerTestHelper.class.getName(),
                        "retry-seq", stateFile.toString(),
                        Integer.toString(failCount), Integer.toString(successExitCode)))
                .build();
    }

    private String javaExecutable() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String name = os.contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", name).toString();
    }

}
