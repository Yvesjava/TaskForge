package cn.iocoder.yudao.module.agent.framework.exec;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Codex/Claude 子进程执行配置
 *
 * <p>集中管理 AI 执行器的可执行文件、单次执行超时与输出捕获上限，
 * 避免在业务代码中硬编码进程参数。
 *
 * @author TaskForge
 */
@Data
@Component
@ConfigurationProperties(prefix = "yudao.agent.exec")
@Validated
public class AgentExecProperties {

    /**
     * Codex/Claude 可执行文件（默认 codex，可按部署切换为 claude）
     */
    private String executable = "codex";

    /**
     * 单次执行超时，超时后终止整个进程树
     */
    private Duration timeout = Duration.ofMinutes(30);

    /**
     * 自修复重试次数上限；初始执行失败后最多重试该次数，超过后归档失败结果
     */
    @Min(0)
    private int maxRetries = 2;

    /**
     * stdout/stderr 单流捕获上限（字节），超出保留头尾并插入截断标记
     */
    private int maxOutputBytes = 256 * 1024;

    /**
     * 构建/测试命令白名单（可执行文件基名，不区分大小写）
     */
    private List<String> allowedCommands = new ArrayList<>(List.of(
            "mvn", "mvnw", "gradle", "npm", "pnpm", "yarn", "npx",
            "make", "go", "dotnet", "pytest", "python", "python3", "node", "java"));

}
