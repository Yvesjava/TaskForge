package cn.iocoder.yudao.module.agent.framework.exec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * {@link CodexRunner} 的契约测试
 *
 * <p>使用真实 JVM 子进程充当假 Codex，验证 stdout/stderr、退出码、PID 与
 * 启动参数可记录，并验证超时后整个进程树（父进程及其派生子进程）被终止。
 */
class CodexRunnerTest {

    @TempDir
    Path tempDir;

    private final CodexRunner runner = new CodexRunner(new AgentExecProperties());

    @Test
    void run_recordsStdoutStderrExitCodePidAndStartupArgs() throws Exception {
        List<String> arguments = List.of("-cp", System.getProperty("java.class.path"),
                CodexRunnerTestHelper.class.getName(), "basic", "7");

        CodexRunResult result = runner.run(CodexRunRequest.builder()
                .workingDirectory(tempDir)
                .executable(javaExecutable())
                .arguments(arguments)
                .build());

        assertThat(result.exitCode()).isEqualTo(7);
        assertThat(result.pid()).isGreaterThan(0L);
        assertThat(result.commandLine()).containsExactly(
                javaExecutable(), "-cp", System.getProperty("java.class.path"),
                CodexRunnerTestHelper.class.getName(), "basic", "7");
        assertThat(result.stdout()).contains(CodexRunnerTestHelper.STDOUT_MARKER);
        assertThat(result.stderr()).contains(CodexRunnerTestHelper.STDERR_MARKER);
        assertThat(result.stdout()).doesNotContain(CodexRunnerTestHelper.STDERR_MARKER);
        assertThat(result.timedOut()).isFalse();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.durationMillis()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void run_returnsSuccessOnZeroExitCode() throws Exception {
        CodexRunResult result = runner.run(CodexRunRequest.builder()
                .workingDirectory(tempDir)
                .executable(javaExecutable())
                .arguments(List.of("-cp", System.getProperty("java.class.path"),
                        CodexRunnerTestHelper.class.getName(), "basic", "0"))
                .build());

        assertThat(result.exitCode()).isZero();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stdout()).contains(CodexRunnerTestHelper.STDOUT_MARKER);
    }

    @Test
    void run_timesOutAndTerminatesWholeProcessGroup() throws Exception {
        Path pidFile = tempDir.resolve("pids.txt");

        CodexRunResult result = runner.run(CodexRunRequest.builder()
                .workingDirectory(tempDir)
                .executable(javaExecutable())
                .arguments(List.of("-cp", System.getProperty("java.class.path"),
                        CodexRunnerTestHelper.class.getName(), "spawn-child", pidFile.toString()))
                .timeout(Duration.ofSeconds(8))
                .build());

        assertThat(result.timedOut()).isTrue();
        String pids = Files.readString(pidFile);
        assertThat(pids).isNotBlank();
        String[] parts = pids.trim().split("\\s+");
        awaitTerminated(Long.parseLong(parts[0]));
        awaitTerminated(Long.parseLong(parts[1]));
    }

    private void awaitTerminated(long pid) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(15);
        while (System.currentTimeMillis() < deadline) {
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            if (handle.isEmpty() || !handle.get().isAlive()) {
                return;
            }
            Thread.sleep(100);
        }
        fail("进程 " + pid + " 未在超时前终止");
    }

    private String javaExecutable() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String name = os.contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", name).toString();
    }

}
