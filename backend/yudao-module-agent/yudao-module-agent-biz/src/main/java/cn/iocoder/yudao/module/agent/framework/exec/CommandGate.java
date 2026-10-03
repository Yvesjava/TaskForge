package cn.iocoder.yudao.module.agent.framework.exec;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 构建/测试命令白名单与顺序执行门禁
 *
 * <p>验收命令必须来自白名单校验的构建/测试工具；执行时按声明顺序串行运行，
 * 任意一条命令以非零退出码或超时结束都会停止后续命令，并禁止提交与推送。
 * 只有全部验收命令通过后，{@link CommandGateResult#isCommitAllowed()} 才返回
 * {@code true}。
 *
 * @author TaskForge
 */
@Component
public class CommandGate {

    private final CommandExecutor executor;

    private final Set<String> allowedExecutables;

    public CommandGate(CommandExecutor executor, AgentExecProperties properties) {
        this.executor = executor;
        this.allowedExecutables = normalizeAllowed(properties.getAllowedCommands());
    }

    /**
     * 判断可执行文件是否在白名单内。
     */
    public boolean isAllowed(String executable) {
        return allowedExecutables.contains(normalizeExecutable(executable));
    }

    /**
     * 校验单条命令；不在白名单内时抛出 {@link CommandNotAllowedException}。
     */
    public void validate(CommandSpec command) {
        if (command == null || !isAllowed(command.executable())) {
            throw new CommandNotAllowedException(command == null ? null : command.executable());
        }
    }

    /**
     * 先校验全部命令的白名单，再按顺序串行执行；任一命令失败即停止。
     *
     * @param commands         验收命令列表（按声明顺序）
     * @param workingDirectory 子进程工作目录
     * @return 门禁执行结果
     */
    public CommandGateResult execute(List<CommandSpec> commands, Path workingDirectory) {
        List<CommandSpec> snapshot = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
        for (CommandSpec command : snapshot) {
            validate(command);
        }

        List<CommandStepResult> steps = new ArrayList<>();
        for (CommandSpec command : snapshot) {
            CommandStepResult step = executor.execute(command, workingDirectory);
            steps.add(step);
            if (!step.isSuccess()) {
                break;
            }
        }
        return new CommandGateResult(steps, snapshot.size());
    }

    private static Set<String> normalizeAllowed(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String command : commands) {
            String value = normalizeExecutable(command);
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return Set.copyOf(normalized);
    }

    private static String normalizeExecutable(String executable) {
        if (executable == null) {
            return "";
        }
        String value = executable.trim();
        if (value.isEmpty()) {
            return "";
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        String base = slash >= 0 ? value.substring(slash + 1) : value;
        String lower = base.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".exe") || lower.endsWith(".cmd") || lower.endsWith(".bat")) {
            base = base.substring(0, base.length() - 4);
        }
        return base.toLowerCase(Locale.ROOT);
    }

}
