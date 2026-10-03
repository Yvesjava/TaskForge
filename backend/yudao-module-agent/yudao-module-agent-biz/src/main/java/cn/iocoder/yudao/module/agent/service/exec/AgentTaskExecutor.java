package cn.iocoder.yudao.module.agent.service.exec;

import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.framework.exec.CodexRetryResult;
import cn.iocoder.yudao.module.agent.framework.exec.CodexRetryRunner;
import cn.iocoder.yudao.module.agent.framework.exec.CodexRunAttempt;
import cn.iocoder.yudao.module.agent.framework.exec.CodexRunRequest;
import cn.iocoder.yudao.module.agent.framework.exec.CommandGate;
import cn.iocoder.yudao.module.agent.framework.exec.CommandGateResult;
import cn.iocoder.yudao.module.agent.framework.exec.CommandStepResult;
import cn.iocoder.yudao.module.agent.framework.secret.SecretRedactor;
import cn.iocoder.yudao.module.agent.service.security.SecurityPolicy;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskStateMachine;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskTransitionCommand;
import cn.iocoder.yudao.module.agent.service.workspace.CompositeWorkspace;
import cn.iocoder.yudao.module.agent.service.workspace.TaskBranchManager;
import cn.iocoder.yudao.module.agent.service.workspace.WorktreeManager;
import cn.iocoder.yudao.module.agent.service.workspace.WorkspaceProject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * AI 执行与自验编排器
 *
 * <p>负责在任务已抢占为 {@code RUNNING} 后串联完整执行链：创建聚合工作区、
 * 运行 Codex/Claude（含超时熔断与自修复重试）、按白名单顺序执行验收命令，
 * 并在成功时写回结果进入 {@code WAITING_ACCEPTANCE}，失败或超时时清理工作区与
 * 分支并写回结果进入 {@code FAILED}。</p>
 *
 * <p>状态变更统一通过 {@link AgentTaskStateMachine} 完成，本类不直接更新任务
 * 主表；结果字段（执行日志、重试次数、耗时、工作区路径）作为转换命令的一部分
 * 由状态机在同一条件更新中原子写回，避免旧 Worker 覆盖新结果。</p>
 *
 * @author TaskForge
 */
@Service
@Slf4j
public class AgentTaskExecutor {

    private final WorktreeManager worktreeManager;

    private final TaskBranchManager taskBranchManager;

    private final CodexRetryRunner codexRunner;

    private final CommandGate commandGate;

    private final SecurityPolicy securityPolicy;

    private final AgentTaskStateMachine stateMachine;

    public AgentTaskExecutor(WorktreeManager worktreeManager,
                             TaskBranchManager taskBranchManager,
                             CodexRetryRunner codexRunner,
                             CommandGate commandGate,
                             SecurityPolicy securityPolicy,
                             AgentTaskStateMachine stateMachine) {
        this.worktreeManager = Objects.requireNonNull(worktreeManager, "worktreeManager 不能为空");
        this.taskBranchManager = Objects.requireNonNull(taskBranchManager, "taskBranchManager 不能为空");
        this.codexRunner = Objects.requireNonNull(codexRunner, "codexRunner 不能为空");
        this.commandGate = Objects.requireNonNull(commandGate, "commandGate 不能为空");
        this.securityPolicy = Objects.requireNonNull(securityPolicy, "securityPolicy 不能为空");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine 不能为空");
    }

    /**
     * 执行一次任务，并返回写回后的最终状态与统计。
     *
     * @param request 执行请求
     * @return 执行结果
     */
    public ExecutionOutcome execute(ExecutionRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        long started = System.currentTimeMillis();

        CompositeWorkspace workspace = null;
        CodexRetryResult codex = null;
        CommandGateResult gate = null;
        boolean timedOut = false;
        Throwable error = null;

        try {
            securityPolicy.checkExecution(request.getTaskNo(), request.getTargetBranch(),
                    request.getProjects(), request.getVerificationCommands());
        } catch (RuntimeException e) {
            error = e;
            log.error("[AgentTaskExecutor] 安全策略校验失败 taskNo={}, workerId={}, generation={}",
                    request.getTaskNo(), request.getWorkerId(), request.getGeneration(), e);
        }

        if (error == null) {
            try {
                workspace = worktreeManager.createCompositeWorkspace(
                        request.getTaskNo(), request.getTargetBranch(), request.getTaskDoc(), request.getProjects());
            } catch (RuntimeException e) {
                error = e;
                log.error("[AgentTaskExecutor] 创建工作区失败 taskNo={}, workerId={}, generation={}",
                        request.getTaskNo(), request.getWorkerId(), request.getGeneration(), e);
            }
        }

        if (workspace != null) {
            try {
                codex = codexRunner.run(CodexRunRequest.builder()
                        .workingDirectory(workspace.getRoot())
                        .executable(request.getExecutable())
                        .arguments(request.getArguments())
                        .timeout(request.getTimeout())
                        .build());
                timedOut = codex.lastAttempt() != null && codex.lastAttempt().timedOut();
                if (codex.isSuccess()) {
                    gate = commandGate.execute(request.getVerificationCommands(), workspace.getRoot());
                }
            } catch (RuntimeException e) {
                error = e;
                log.error("[AgentTaskExecutor] Codex/验收执行异常 taskNo={}, workerId={}, generation={}",
                        request.getTaskNo(), request.getWorkerId(), request.getGeneration(), e);
            }
        }

        boolean success = error == null
                && codex != null && codex.isSuccess()
                && (gate == null || gate.isCommitAllowed());
        AgentTaskAction action = success ? AgentTaskAction.SELF_VERIFY_PASS
                : timedOut ? AgentTaskAction.TIMEOUT : AgentTaskAction.ERROR;

        if (!success) {
            cleanupQuietly(request);
        }

        String executionLog = renderExecutionLog(codex, gate, error);
        int retryTimes = codex == null ? 0 : codex.retryCount();
        long costMs = System.currentTimeMillis() - started;

        AgentTaskStatus status = stateMachine.transition(AgentTaskTransitionCommand.builder()
                .taskId(request.getTaskId())
                .taskNo(request.getTaskNo())
                .action(action)
                .fromStatus(AgentTaskStatus.RUNNING)
                .workerId(request.getWorkerId())
                .generation(request.getGeneration())
                .executionLog(executionLog)
                .retryTimes(retryTimes)
                .costMs(costMs)
                .diffStat(null)
                .workspacePath(success && workspace != null ? workspace.getRoot().toString() : null)
                .build());

        log.info("[AgentTaskExecutor] 执行完成 taskNo={}, action={}, status={}, timedOut={}, retryTimes={}, costMs={}",
                request.getTaskNo(), action.getValue(), status.getValue(), timedOut, retryTimes, costMs);
        return new ExecutionOutcome(status, timedOut, retryTimes, costMs, executionLog);
    }

    /**
     * 失败清理：幂等销毁聚合工作区并删除各项目特性分支，任何一步失败都不阻断
     * 后续状态写回，仅记录告警，避免把可回收资源伪装成 FAILED。
     */
    private void cleanupQuietly(ExecutionRequest request) {
        try {
            worktreeManager.destroyCompositeWorkspaceIfPresent(request.getTaskNo());
        } catch (RuntimeException e) {
            log.error("[AgentTaskExecutor] 失败清理工作区异常 taskNo={}", request.getTaskNo(), e);
        }

        if (request.getTargetBranch() == null || request.getTargetBranch().isBlank()) {
            return;
        }
        for (WorkspaceProject project : request.getProjects()) {
            try {
                taskBranchManager.deleteFeatureBranch(project.getProjectCode(), request.getTargetBranch());
            } catch (RuntimeException e) {
                log.error("[AgentTaskExecutor] 失败清理分支异常 taskNo={}, project={}",
                        request.getTaskNo(), project.getProjectCode(), e);
            }
        }
    }

    private String renderExecutionLog(CodexRetryResult codex, CommandGateResult gate, Throwable error) {
        StringBuilder builder = new StringBuilder();
        builder.append("=== Codex attempts ===\n");
        if (codex == null || codex.attempts().isEmpty()) {
            builder.append("(no codex attempt)\n");
        } else {
            for (CodexRunAttempt attempt : codex.attempts()) {
                builder.append("--- attempt ").append(attempt.attemptNumber())
                        .append(" exit=").append(attempt.exitCode())
                        .append(" timedOut=").append(attempt.timedOut())
                        .append(" durationMs=").append(attempt.durationMillis())
                        .append(" ---\n");
                builder.append("stdout:\n").append(attempt.stdout()).append('\n');
                builder.append("stderr:\n").append(attempt.stderr()).append('\n');
            }
        }

        builder.append("=== Verification ===\n");
        if (gate == null || gate.steps().isEmpty()) {
            builder.append("(no verification command executed)\n");
        } else {
            for (CommandStepResult step : gate.steps()) {
                builder.append("--- command ").append(step.executable())
                        .append(" exit=").append(step.exitCode())
                        .append(" timedOut=").append(step.timedOut())
                        .append(" durationMs=").append(step.durationMillis())
                        .append(" ---\n");
                builder.append(step.output()).append('\n');
            }
        }

        if (error != null) {
            builder.append("=== Error ===\n")
                    .append(error.getClass().getName()).append(": ")
                    .append(SecretRedactor.redact(error.getMessage() == null ? "" : error.getMessage()))
                    .append('\n');
        }
        return builder.toString();
    }

}
