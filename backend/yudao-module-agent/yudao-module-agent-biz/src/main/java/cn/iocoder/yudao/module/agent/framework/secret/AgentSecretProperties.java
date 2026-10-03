package cn.iocoder.yudao.module.agent.framework.secret;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 凭证管理器配置
 *
 * <p>以 {@code yudao.agent.secret.values} 维护凭证引用到凭证值的映射，作为本地
 * 加密配置中心的最小实现；生产环境可通过环境变量覆盖或替换为外部 Secret Manager。
 *
 * @author TaskForge
 */
@Data
@Component
@ConfigurationProperties(prefix = "yudao.agent.secret")
public class AgentSecretProperties {

    /**
     * 凭证引用到凭证值的映射，键对应 {@code agent_project.credential_ref}。
     */
    private Map<String, String> values = new LinkedHashMap<>();

}
