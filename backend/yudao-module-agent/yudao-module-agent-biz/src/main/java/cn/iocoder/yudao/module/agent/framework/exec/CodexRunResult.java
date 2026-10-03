package cn.iocoder.yudao.module.agent.framework.exec;

import java.util.List;

/**
 * Codex/Claude 子进程执行结果
 *
 * <p>记录退出码、PID、启动参数、stdout/stderr 与是否超时，供上层写入
 * {@code execution_log} 与任务状态回写。
 *
 * @param exitCode       进程退出码
 * @param pid            进程 PID
 * @param commandLine    完整启动命令行（含可执行文件名）
 * @param stdout         标准输出（已按上限截断）
 * @param stderr         标准错误（已按上限截断）
 * @param timedOut       是否因超时被终止
 * @param durationMillis 执行耗时（毫秒）
 * @author TaskForge
 */
public record CodexRunResult(
        int exitCode,
        long pid,
        List<String> commandLine,
        String stdout,
        String stderr,
        boolean timedOut,
        long durationMillis) {

    public CodexRunResult {
        commandLine = List.copyOf(commandLine);
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }

    /**
     * 子进程是否正常完成：退出码为 0 且未超时。
     */
    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }

}
