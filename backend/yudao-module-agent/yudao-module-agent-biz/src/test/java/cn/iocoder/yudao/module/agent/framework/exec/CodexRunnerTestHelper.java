package cn.iocoder.yudao.module.agent.framework.exec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 供 {@link CodexRunnerTest} 使用的假 Codex 子进程。
 *
 * <p>提供三种模式：
 * <ul>
 *   <li>{@code basic <exitCode>}：向 stdout/stderr 各写一行后按退出码退出；</li>
 *   <li>{@code spawn-child <pidFile>}：派生一个长时间休眠的子进程，
 *       将父子 PID 写入文件后等待子进程退出；</li>
 *   <li>{@code retry-seq <stateFile> <failCount> <successExitCode>}：每次调用
 *       将序号写入 stateFile，输出带序号的 stdout/stderr，前 failCount 次
 *       以非零退出码退出，之后以指定退出码退出；</li>
 *   <li>{@code sleep <millis>}：休眠指定毫秒。</li>
 * </ul>
 *
 * @author TaskForge
 */
public final class CodexRunnerTestHelper {

    public static final String STDOUT_MARKER = "CODX-STDOUT-MARKER";

    public static final String STDERR_MARKER = "CODX-STDERR-MARKER";

    private CodexRunnerTestHelper() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.exit(0);
        }
        switch (args[0]) {
            case "basic" -> basic(Integer.parseInt(args[1]));
            case "spawn-child" -> spawnChild(Path.of(args[1]));
            case "retry-seq" -> retrySequence(Path.of(args[1]), Integer.parseInt(args[2]),
                    Integer.parseInt(args[3]));
            case "sleep" -> Thread.sleep(Long.parseLong(args[1]));
            default -> System.exit(2);
        }
    }

    private static void basic(int exitCode) {
        System.out.println(STDOUT_MARKER);
        System.err.println(STDERR_MARKER);
        System.out.flush();
        System.err.flush();
        System.exit(exitCode);
    }

    private static void spawnChild(Path pidFile) throws Exception {
        long parentPid = ProcessHandle.current().pid();
        Process child = new ProcessBuilder(javaExecutable(), "-cp", System.getProperty("java.class.path"),
                CodexRunnerTestHelper.class.getName(), "sleep", "120000").start();
        long childPid = child.pid();
        Files.writeString(pidFile, parentPid + " " + childPid, StandardCharsets.UTF_8);
        child.waitFor();
    }

    private static void retrySequence(Path stateFile, int failCount, int successExitCode) throws Exception {
        int previous = Files.exists(stateFile)
                ? Integer.parseInt(Files.readString(stateFile, StandardCharsets.UTF_8).trim()) : 0;
        int attempt = previous + 1;
        Files.writeString(stateFile, Integer.toString(attempt), StandardCharsets.UTF_8);
        System.out.println("CODX-RETRY-STDOUT-" + attempt);
        System.err.println("CODX-RETRY-STDERR-" + attempt);
        System.out.flush();
        System.err.flush();
        System.exit(attempt <= failCount ? 3 : successExitCode);
    }

    private static String javaExecutable() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String name = os.contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", name).toString();
    }

}
