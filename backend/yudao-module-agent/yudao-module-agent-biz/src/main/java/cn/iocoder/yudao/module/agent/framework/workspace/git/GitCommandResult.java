package cn.iocoder.yudao.module.agent.framework.workspace.git;

/**
 * Git 子进程执行结果
 *
 * <p>用于那些退出码本身携带业务含义的命令（例如 {@code ls-remote --exit-code}
 * 通过退出码 2 表达“远端引用不存在”），调用方需要拿到原始退出码而不是直接抛异常。
 *
 * @param exitCode 进程退出码
 * @param output   合并后的标准输出/标准错误（已截断）
 * @author TaskForge
 */
public record GitCommandResult(int exitCode, String output) {
}
