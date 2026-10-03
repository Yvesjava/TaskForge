package cn.iocoder.yudao.module.agent.framework.exec;

import java.util.List;

/**
 * Codex/Claude 子进程执行异常
 *
 * <p>仅在进程启动失败、等待被中断等无法正常收集退出码的异常路径抛出。
 * 非零退出码与超时都属于可记录的业务结果，由 {@link CodexRunResult} 承载，
 * 不通过异常表达。
 *
 * @author TaskForge
 */
public class CodexProcessException extends RuntimeException {

    private final int exitCode;

    private final long pid;

    private final List<String> commandLine;

    public CodexProcessException(String message, int exitCode, long pid, List<String> commandLine) {
        super(message);
        this.exitCode = exitCode;
        this.pid = pid;
        this.commandLine = List.copyOf(commandLine);
    }

    public CodexProcessException(String message, int exitCode, long pid, List<String> commandLine, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
        this.pid = pid;
        this.commandLine = List.copyOf(commandLine);
    }

    public int getExitCode() {
        return exitCode;
    }

    public long getPid() {
        return pid;
    }

    public List<String> getCommandLine() {
        return commandLine;
    }

}
