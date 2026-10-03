package cn.iocoder.yudao.module.agent.framework.exec;

import java.time.Duration;
import java.util.List;

/**
 * 单条验收命令
 *
 * <p>由 {@link CommandGate} 校验并交给 {@link CommandExecutor} 执行。命令必须
 * 是可执行文件 + 参数的结构化描述，不允许传入 shell 拼接字符串，避免命令注入。
 *
 * @param executable 可执行文件（如 mvn、pnpm）
 * @param arguments  命令参数（不含可执行文件名）
 * @param timeout    单条命令超时；为空时由执行器使用默认值
 * @author TaskForge
 */
public record CommandSpec(String executable, List<String> arguments, Duration timeout) {

    public CommandSpec {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    /**
     * 生成面向日志与报告的展示文本（不含敏感信息）。
     */
    public String display() {
        return executable + (arguments.isEmpty() ? "" : " " + String.join(" ", arguments));
    }

}
