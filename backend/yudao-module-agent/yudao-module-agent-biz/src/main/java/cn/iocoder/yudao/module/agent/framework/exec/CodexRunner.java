package cn.iocoder.yudao.module.agent.framework.exec;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Codex/Claude 非交互子进程执行器
 *
 * <p>以非交互批处理模式启动 Codex 或 Claude，工作目录固定为已经过校验的
 * 聚合工作区根目录，禁止等待终端人工输入。执行期间分别捕获 stdout 与
 * stderr 到临时文件，并记录 PID、启动参数、退出码与耗时。
 *
 * <p>进程组管理通过 {@link ProcessHandle#descendants()} 覆盖 Codex 派生出的
 * 编译器、测试等整个进程树：超时或调用方取消时先终止所有子进程，再终止
 * 根进程，避免残留僵尸进程。该策略在 Windows 与 Linux 上行为一致。
 *
 * @author TaskForge
 */
@Component
@Slf4j
public class CodexRunner {

    /**
     * 强杀进程组后的宽限等待时长（毫秒）
     */
    private static final long KILL_GRACE_MILLIS = 30_000L;

    private final AgentExecProperties properties;

    public CodexRunner(AgentExecProperties properties) {
        this.properties = properties;
    }

    /**
     * 使用默认可执行文件执行子进程。
     *
     * @param workingDirectory 聚合工作区根目录
     * @param arguments        启动参数（不含可执行文件名）
     * @return 子进程执行结果
     */
    public CodexRunResult run(Path workingDirectory, List<String> arguments) {
        return run(CodexRunRequest.builder()
                .workingDirectory(workingDirectory)
                .arguments(arguments)
                .build());
    }

    /**
     * 启动并等待子进程结束，返回完整执行结果。
     *
     * @param request 启动请求
     * @return 子进程执行结果（含 stdout/stderr、退出码、PID、启动参数）
     */
    public CodexRunResult run(CodexRunRequest request) {
        Path workingDirectory = Objects.requireNonNull(request.getWorkingDirectory(), "workingDirectory 不能为空");
        String executable = resolveExecutable(request.getExecutable());
        List<String> arguments = request.getArguments() == null
                ? List.of() : List.copyOf(request.getArguments());
        Duration timeout = resolveTimeout(request.getTimeout());

        List<String> command = new ArrayList<>(arguments.size() + 1);
        command.add(executable);
        command.addAll(arguments);
        List<String> commandLine = List.copyOf(command);

        Path stdoutFile = null;
        Path stderrFile = null;
        Process process = null;
        long started = System.currentTimeMillis();
        try {
            stdoutFile = Files.createTempFile("taskforge-codex-out-", ".log");
            stderrFile = Files.createTempFile("taskforge-codex-err-", ".log");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory.toFile());
            builder.redirectOutput(ProcessBuilder.Redirect.to(stdoutFile.toFile()));
            builder.redirectError(ProcessBuilder.Redirect.to(stderrFile.toFile()));
            process = builder.start();
            long pid = process.pid();
            log.info("[CodexRunner] 启动子进程 pid={}, executable={}, argCount={}, timeout={}",
                    pid, executable, arguments.size(), timeout);

            boolean finished;
            try {
                finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                destroyProcessGroup(process);
                throw new CodexProcessException("Codex 子进程等待被中断", -1, pid, commandLine, e);
            }

            boolean timedOut = false;
            if (!finished) {
                timedOut = true;
                log.warn("[CodexRunner] 子进程超时 pid={}, executable={}，终止整个进程树", pid, executable);
                destroyProcessGroup(process);
                awaitTermination(process, pid);
            }

            int exitCode = process.exitValue();
            long duration = System.currentTimeMillis() - started;
            String stdout = readBounded(stdoutFile);
            String stderr = readBounded(stderrFile);
            return new CodexRunResult(exitCode, pid, commandLine, stdout, stderr, timedOut, duration);
        } catch (IOException e) {
            throw new CodexProcessException("无法启动 Codex 子进程：" + reasonOf(e), -1,
                    process == null ? -1L : process.pid(), commandLine, e);
        } finally {
            deleteQuietly(stdoutFile);
            deleteQuietly(stderrFile);
        }
    }

    /**
     * 终止根进程及其全部后代，先子后父，避免遗留孤儿进程。
     */
    private void destroyProcessGroup(Process process) {
        ProcessHandle self = process.toHandle();
        self.descendants().forEach(ProcessHandle::destroyForcibly);
        self.destroyForcibly();
    }

    private void awaitTermination(Process process, long pid) {
        try {
            if (!process.waitFor(KILL_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(KILL_GRACE_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            log.warn("[CodexRunner] 等待进程终止被中断 pid={}", pid);
        }
    }

    private String resolveExecutable(String executable) {
        String value = executable == null || executable.isBlank()
                ? properties.getExecutable() : executable.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Codex/Claude 可执行文件不能为空");
        }
        return value;
    }

    private Duration resolveTimeout(Duration timeout) {
        if (timeout != null) {
            return timeout;
        }
        Duration defaultTimeout = properties.getTimeout();
        return defaultTimeout == null ? Duration.ofMinutes(30) : defaultTimeout;
    }

    private String readBounded(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            int maxBytes = properties.getMaxOutputBytes();
            if (maxBytes <= 0 || bytes.length <= maxBytes) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            int keep = maxBytes / 2;
            String head = new String(bytes, 0, keep, StandardCharsets.UTF_8);
            String tail = new String(bytes, bytes.length - keep, keep, StandardCharsets.UTF_8);
            return head + "\n...[truncated, " + bytes.length + " bytes]...\n" + tail;
        } catch (IOException e) {
            return "";
        }
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 临时文件清理失败不影响主流程
        }
    }

    private String reasonOf(IOException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

}
