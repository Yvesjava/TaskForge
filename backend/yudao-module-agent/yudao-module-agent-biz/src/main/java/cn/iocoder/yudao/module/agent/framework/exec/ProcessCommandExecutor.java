package cn.iocoder.yudao.module.agent.framework.exec;

import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
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
 * 基于 {@link ProcessBuilder} 的验收命令执行器
 *
 * <p>合并 stdout/stderr 到临时文件避免管道死锁，超时强制终止进程，并返回
 * 退出码与截断后的输出。日志仅记录可执行文件与参数个数，不落盘原始参数，
 * 避免泄露凭证。
 *
 * @author TaskForge
 */
@Component
@Slf4j
public class ProcessCommandExecutor implements CommandExecutor {

    /**
     * 单条命令默认超时
     */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);

    /**
     * 终止超时进程后的宽限等待时长
     */
    private static final long TERMINATION_GRACE_SECONDS = 30L;

    /**
     * 输出读取上限（字节），超出保留头尾并插入截断标记
     */
    private static final int MAX_OUTPUT_BYTES = 256 * 1024;

    private final AgentExecProperties properties;

    public ProcessCommandExecutor() {
        this(new AgentExecProperties());
    }

    public ProcessCommandExecutor(AgentExecProperties properties) {
        this.properties = properties == null ? new AgentExecProperties() : properties;
    }

    @Override
    public CommandStepResult execute(CommandSpec command, Path workingDirectory) {
        Objects.requireNonNull(command, "command 不能为空");
        Objects.requireNonNull(workingDirectory, "workingDirectory 不能为空");

        List<String> commandLine = new ArrayList<>(command.arguments().size() + 1);
        commandLine.add(command.executable());
        commandLine.addAll(command.arguments());

        Duration timeout = command.timeout() == null ? resolveCommandTimeout() : command.timeout();

        Path outputFile = null;
        Process process = null;
        long started = System.currentTimeMillis();
        try {
            outputFile = Files.createTempFile("taskforge-command-", ".log");
            ProcessBuilder builder = new ProcessBuilder(commandLine);
            builder.directory(workingDirectory.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.to(outputFile.toFile()));
            process = builder.start();

            boolean finished;
            try {
                finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new CommandExecutionException("验收命令等待被中断：" + command.display(), e);
            }

            boolean timedOut = false;
            if (!finished) {
                timedOut = true;
                log.warn("[CommandExecutor] 验收命令超时 executable={}, timeout={}, traceId={}",
                        command.executable(), timeout, TracerUtils.getTraceId());
                process.destroyForcibly();
                try {
                    if (!process.waitFor(TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        process.waitFor(TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            }

            int exitCode = process.exitValue();
            long duration = System.currentTimeMillis() - started;
            String output = readBounded(outputFile);
            if (exitCode != 0) {
                log.warn("[CommandExecutor] 验收命令非零退出 executable={}, exitCode={}, durationMs={}, traceId={}",
                        command.executable(), exitCode, duration, TracerUtils.getTraceId());
            }
            return new CommandStepResult(command.executable(), command.arguments(),
                    exitCode, output, timedOut, duration);
        } catch (IOException e) {
            throw new CommandExecutionException("无法启动验收命令：" + command.display(), e);
        } finally {
            deleteQuietly(outputFile);
        }
    }

    private String readBounded(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            int maxOutputBytes = maxOutputBytes();
            if (bytes.length <= maxOutputBytes) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            int keep = maxOutputBytes / 2;
            String head = new String(bytes, 0, keep, StandardCharsets.UTF_8);
            String tail = new String(bytes, bytes.length - keep, keep, StandardCharsets.UTF_8);
            return head + "\n...[truncated, " + bytes.length + " bytes]...\n" + tail;
        } catch (IOException e) {
            return "<无法读取验收命令输出：" + e.getMessage() + ">";
        }
    }

    private Duration resolveCommandTimeout() {
        return properties.getCommandTimeout() == null ? DEFAULT_TIMEOUT : properties.getCommandTimeout();
    }

    private int maxOutputBytes() {
        return properties.getMaxOutputBytes() > 0 ? properties.getMaxOutputBytes() : MAX_OUTPUT_BYTES;
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

}
