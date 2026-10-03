package cn.iocoder.yudao.module.agent.framework.exec;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

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
     * stdout/stderr 单流捕获上限（字节），超出保留头尾并插入截断标记
     */
    private int maxOutputBytes = 256 * 1024;

}
