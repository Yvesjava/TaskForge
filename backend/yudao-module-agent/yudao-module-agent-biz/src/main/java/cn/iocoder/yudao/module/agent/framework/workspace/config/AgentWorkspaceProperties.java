package cn.iocoder.yudao.module.agent.framework.workspace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * TaskForge 工作区相关配置
 *
 * <p>集中管理宿主机上的 Git 裸仓库缓存与工作区根目录，
 * 避免在业务代码中硬编码路径。
 *
 * @author TaskForge
 */
@Data
@Component
@ConfigurationProperties(prefix = "yudao.agent.workspace")
@Validated
public class AgentWorkspaceProperties {

    /**
     * Bare Repo 缓存根目录
     */
    private String bareRepoRoot = "/data/agent-bare-repos";

}
