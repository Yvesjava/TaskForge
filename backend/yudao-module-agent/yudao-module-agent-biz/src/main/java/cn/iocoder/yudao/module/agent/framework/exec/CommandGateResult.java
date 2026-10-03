package cn.iocoder.yudao.module.agent.framework.exec;

import java.util.List;

/**
 * 验收命令门禁执行结果
 *
 * <p>承载按顺序执行的命令结果，并据此判断是否允许提交与推送。仅当全部
 * 验收命令均以退出码 0 完成（且未超时）时才允许提交/推送。
 *
 * @param steps         已执行的命令结果（顺序与输入一致）
 * @param totalCommands 本次验收声明的命令总数
 * @author TaskForge
 */
public record CommandGateResult(List<CommandStepResult> steps, int totalCommands) {

    public CommandGateResult {
        steps = List.copyOf(steps);
    }

    /**
     * 是否已执行完全部声明的命令。
     */
    public boolean isComplete() {
        return steps.size() == totalCommands;
    }

    /**
     * 是否所有验收命令均通过。
     */
    public boolean isAllPassed() {
        return isComplete() && steps.stream().allMatch(CommandStepResult::isSuccess);
    }

    /**
     * 是否允许提交代码；只有所有验收命令通过后才允许。
     */
    public boolean isCommitAllowed() {
        return isAllPassed();
    }

    /**
     * 是否允许推送分支；只有所有验收命令通过后才允许。
     */
    public boolean isPushAllowed() {
        return isAllPassed();
    }

    /**
     * 返回第一条失败命令，全部通过时返回 {@code null}。
     */
    public CommandStepResult failure() {
        return steps.stream().filter(step -> !step.isSuccess()).findFirst().orElse(null);
    }

}
