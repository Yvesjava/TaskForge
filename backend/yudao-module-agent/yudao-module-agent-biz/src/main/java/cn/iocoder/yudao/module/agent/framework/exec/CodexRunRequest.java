package cn.iocoder.yudao.module.agent.framework.exec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Codex/Claude 子进程启动请求
 *
 * <p>由上层 Worker 在任务工作区创建完成后组装，{@link CodexRunner} 仅按
 * 该契约启动子进程，不直接接触任务数据模型。
 *
 * @author TaskForge
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodexRunRequest {

    /**
     * 子进程工作目录（聚合工作区根目录）
     */
    private Path workingDirectory;

    /**
     * 可执行文件；为空时使用 {@link AgentExecProperties#getExecutable()}
     */
    private String executable;

    /**
     * 启动参数（不含可执行文件名）
     */
    @Builder.Default
    private List<String> arguments = new ArrayList<>();

    /**
     * 单次执行超时；为空时使用 {@link AgentExecProperties#getTimeout()}
     */
    private Duration timeout;

}
