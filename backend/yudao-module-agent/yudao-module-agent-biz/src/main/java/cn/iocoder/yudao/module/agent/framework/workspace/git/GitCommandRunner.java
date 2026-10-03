package cn.iocoder.yudao.module.agent.framework.workspace.git;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Git 子进程执行器
 *
 * <p>把标准输出与标准错误合并写入临时文件，避免进程输出填满管道导致
 * 死锁，并在超时或非零退出码时抛出 {@link GitCommandException}。
 * 日志输出不包含原始命令参数，防止泄露仓库凭证。
 *
 * @author TaskForge
 */
@Component
public class GitCommandRunner {

    /**
     * 默认子进程超时（秒）
     */
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    /**
     * 输出文件读取上限（字节）
     */
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    /**
     * 在指定工作目录执行 Git 命令。
     *
     * @param workingDirectory 子进程工作目录
     * @param args             Git 参数（不含 git 可执行文件名）
     * @return 合并后的标准输出/标准错误
     * @throws GitCommandException 命令启动失败、超时或非零退出码时抛出
     */
    public String run(Path workingDirectory, List<String> args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);

        Path outputFile = null;
        try {
            outputFile = Files.createTempFile("taskforge-git-", ".log");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.to(outputFile.toFile()));
            Process process = builder.start();

            boolean finished;
            try {
                finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new GitCommandException("git 命令被中断", -1, readBounded(outputFile));
            }

            if (!finished) {
                process.destroyForcibly();
                throw new GitCommandException("git 命令执行超时", -1, readBounded(outputFile));
            }

            int exitCode = process.exitValue();
            String output = readBounded(outputFile);
            if (exitCode != 0) {
                throw new GitCommandException("git 命令执行失败", exitCode, output);
            }
            return output;
        } catch (IOException e) {
            throw new GitCommandException("无法执行 git 命令：" + e.getMessage(), -1, "");
        } finally {
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException ignored) {
                    // 临时文件清理失败不影响主流程
                }
            }
        }
    }

    /**
     * 读取输出文件并限制长度，超长时保留头尾并插入截断标记。
     */
    private String readBounded(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length <= MAX_OUTPUT_BYTES) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            int keep = MAX_OUTPUT_BYTES / 2;
            String head = new String(bytes, 0, keep, StandardCharsets.UTF_8);
            String tail = new String(bytes, bytes.length - keep, keep, StandardCharsets.UTF_8);
            return head + "\n...[truncated, " + bytes.length + " bytes]...\n" + tail;
        } catch (IOException e) {
            return "<无法读取 git 输出：" + e.getMessage() + ">";
        }
    }

}
