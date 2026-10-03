package cn.iocoder.yudao.module.agent.framework.exec;

import cn.iocoder.yudao.module.agent.framework.secret.SecretRedactor;

import java.util.List;

/**
 * 单条验收命令的执行结果
 *
 * @param executable     可执行文件
 * @param arguments      命令参数
 * @param exitCode       进程退出码
 * @param output         合并后的标准输出/标准错误（已截断）
 * @param timedOut       是否因超时被终止
 * @param durationMillis 执行耗时（毫秒）
 * @author TaskForge
 */
public record CommandStepResult(
        String executable,
        List<String> arguments,
        int exitCode,
        String output,
        boolean timedOut,
        long durationMillis) {

    public CommandStepResult {
        arguments = List.copyOf(arguments == null ? List.of() : arguments);
        output = output == null ? "" : output;
    }

    /**
     * 该命令是否正常完成：退出码为 0 且未超时。
     */
    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }

    /**
     * 生成面向日志与报告的展示文本。
     */
    public String display() {
        String redactedArguments = String.join(" ", SecretRedactor.redactArguments(arguments, List.of()));
        return executable + (redactedArguments.isEmpty() ? "" : " " + redactedArguments);
    }

}
