package cn.iocoder.yudao.module.agent.framework.exec;

/**
 * 命令白名单校验异常
 *
 * <p>当验收命令不在允许的构建/测试命令白名单内时抛出，阻止其被提交到
 * 子进程执行。
 *
 * @author TaskForge
 */
public class CommandNotAllowedException extends RuntimeException {

    private final String command;

    public CommandNotAllowedException(String command) {
        super("验收命令不在白名单内：" + (command == null ? "<null>" : command));
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

}
