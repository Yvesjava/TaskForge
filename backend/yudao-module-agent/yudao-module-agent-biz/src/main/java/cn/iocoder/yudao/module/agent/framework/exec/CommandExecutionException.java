package cn.iocoder.yudao.module.agent.framework.exec;

/**
 * 验收命令执行异常
 *
 * <p>仅在命令无法启动、等待被中断等无法正常收集退出码的路径抛出。非零
 * 退出码与超时属于可记录的业务结果，由 {@link CommandStepResult} 承载。
 *
 * @author TaskForge
 */
public class CommandExecutionException extends RuntimeException {

    public CommandExecutionException(String message) {
        super(message);
    }

    public CommandExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

}
