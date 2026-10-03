package cn.iocoder.yudao.module.agent.service.exec;

import cn.iocoder.yudao.module.agent.framework.exec.CommandSpec;
import cn.iocoder.yudao.module.agent.service.workspace.WorkspaceProject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 单次任务执行请求
 *
 * <p>由 Worker 在任务被抢占为 {@code RUNNING} 后组装，{@link AgentTaskExecutor}
 * 仅按该契约编排工作区、Codex 与验收命令，不直接接触调度或租约逻辑。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRequest {

    /**
     * 任务主键 ID
     */
    private Long taskId;

    /**
     * 任务唯一编号
     */
    private String taskNo;

    /**
     * 本次任务特性分支
     */
    private String targetBranch;

    /**
     * 原始任务文档
     */
    private String taskDoc;

    /**
     * 当前 Worker 身份
     */
    private String workerId;

    /**
     * 当前执行代次，防止旧 Worker 覆盖新结果
     */
    private Long generation;

    /**
     * 单次执行硬超时；为空时由 CodexRunner 使用默认配置
     */
    private Duration timeout;

    /**
     * 任务引用的代码项目映射（可为空，表示无工作区挂载）
     */
    @Builder.Default
    private List<WorkspaceProject> projects = new ArrayList<>();

    /**
     * 验收命令（按声明顺序串行执行）
     */
    @Builder.Default
    private List<CommandSpec> verificationCommands = new ArrayList<>();

    /**
     * Codex/Claude 可执行文件；为空时使用 {@code AgentExecProperties} 默认值
     */
    private String executable;

    /**
     * Codex/Claude 启动参数（不含可执行文件名）
     */
    @Builder.Default
    private List<String> arguments = new ArrayList<>();

}
