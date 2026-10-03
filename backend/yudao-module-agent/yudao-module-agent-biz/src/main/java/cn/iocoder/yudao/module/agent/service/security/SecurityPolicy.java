package cn.iocoder.yudao.module.agent.service.security;

import cn.iocoder.yudao.module.agent.framework.exec.CommandGate;
import cn.iocoder.yudao.module.agent.framework.exec.CommandSpec;
import cn.iocoder.yudao.module.agent.service.project.AgentProjectRef;
import cn.iocoder.yudao.module.agent.service.project.AgentProjectRefValidator;
import cn.iocoder.yudao.module.agent.service.workspace.WorkspaceProject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 统一安全策略
 *
 * <p>横向聚合“路径、命令、仓库与权限”三类安全校验，供执行编排在真正创建工作区与
 * 运行 Codex 前做一次 fail-fast 前置检查：</p>
 * <ul>
 *   <li>路径：拒绝非法任务编号、分支、项目代号、子目录与符号链接越界；</li>
 *   <li>命令：通过 {@link CommandGate} 拒绝白名单之外的验收命令；</li>
 *   <li>仓库与权限：通过 {@link AgentProjectRefValidator} 拒绝不存在、停用或跨租户不可见的项目。</li>
 * </ul>
 *
 * @author TaskForge
 */
@Component
public class SecurityPolicy {

    private final CommandGate commandGate;

    private final AgentProjectRefValidator projectRefValidator;

    public SecurityPolicy(CommandGate commandGate, AgentProjectRefValidator projectRefValidator) {
        this.commandGate = Objects.requireNonNull(commandGate, "commandGate 不能为空");
        this.projectRefValidator = Objects.requireNonNull(projectRefValidator, "projectRefValidator 不能为空");
    }

    /**
     * 执行前置安全校验：任务编号、目标分支、项目映射与验收命令全部合法才放行。
     */
    public void checkExecution(String taskNo, String targetBranch,
                               List<WorkspaceProject> projects, List<CommandSpec> commands) {
        PathSecurityPolicy.requireSafeTaskNo(taskNo);
        PathSecurityPolicy.requireSafeBranch(targetBranch);
        if (projects != null) {
            for (WorkspaceProject project : projects) {
                PathSecurityPolicy.requireSafeProjectCode(project.getProjectCode());
                PathSecurityPolicy.requireSafeBranch(project.getBaseBranch());
                PathSecurityPolicy.requireSafeSubDir(project.getSubDir());
            }
        }
        checkCommands(commands);
    }

    /**
     * 校验验收命令白名单，任一命令不在白名单内即拒绝。
     */
    public void checkCommands(List<CommandSpec> commands) {
        if (commands == null) {
            return;
        }
        for (CommandSpec command : commands) {
            commandGate.validate(command);
        }
    }

    /**
     * 校验任务引用的项目（存在、启用、跨租户可见）。
     */
    public void checkProjectRefs(List<AgentProjectRef> refs) {
        projectRefValidator.validate(refs);
    }

}
