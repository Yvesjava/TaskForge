package cn.iocoder.yudao.module.agent.framework.workspace.git;

/**
 * Git 命令错误归一化工具
 *
 * <p>把底层异常统一转换成可读描述，并在需要时附带仓库与操作上下文，
 * 供各工作区编排类在抛出业务错误时复用，避免错误口径分散在各 Manager。
 *
 * @author TaskForge
 */
public final class GitErrorNormalizer {

    private GitErrorNormalizer() {
    }

    /**
     * 提取底层异常的可读原因（不携带仓库与操作上下文）。
     *
     * @param cause 底层异常
     * @return 可读原因文本
     */
    public static String detail(Exception cause) {
        if (cause instanceof GitCommandException git) {
            String output = git.getSanitizedOutput();
            if (output.isEmpty()) {
                return git.getMessage();
            }
            return git.getMessage() + "：" + output;
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /**
     * 归一化 Git 命令错误，统一附带仓库与操作上下文。
     *
     * @param cause      底层异常
     * @param repository 仓库上下文（项目代号或仓库路径）
     * @param operation  操作上下文（如 worktree remove / worktree prune / clone）
     * @return 归一化后的错误描述
     */
    public static String normalize(Exception cause, String repository, String operation) {
        return "仓库=" + blankToDash(repository)
                + "，操作=" + blankToDash(operation)
                + "，原因=" + detail(cause);
    }

    private static String blankToDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }

}
