package cn.iocoder.yudao.module.agent.framework.exec;

import java.nio.file.Path;

/**
 * 验收命令执行器
 *
 * <p>将结构化命令描述在指定工作目录下执行并返回结果。抽象该接口便于在
 * 单元测试中注入假执行器，验证 {@link CommandGate} 的顺序执行与门禁逻辑。
 *
 * @author TaskForge
 */
@FunctionalInterface
public interface CommandExecutor {

    /**
     * 执行单条验收命令。
     *
     * @param command          验收命令
     * @param workingDirectory 子进程工作目录
     * @return 执行结果
     */
    CommandStepResult execute(CommandSpec command, Path workingDirectory);

}
