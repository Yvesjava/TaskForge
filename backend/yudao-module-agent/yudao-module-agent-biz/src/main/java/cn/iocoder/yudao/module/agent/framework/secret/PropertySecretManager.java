package cn.iocoder.yudao.module.agent.framework.secret;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于配置属性与环境变量的 {@link SecretManager} 实现
 *
 * <p>解析顺序：先查 {@code yudao.agent.secret.values} 映射，再查环境变量
 * {@code TASKFORGE_SECRET_<REFERENCE>}（引用中的非字母数字字符统一替换为下划线并
 * 转为大写）。空值视为未配置，不返回秘密。</p>
 *
 * @author TaskForge
 */
@Component
public class PropertySecretManager implements SecretManager {

    private static final String ENV_PREFIX = "TASKFORGE_SECRET_";

    private final Map<String, String> values;

    public PropertySecretManager(AgentSecretProperties properties) {
        this.values = normalize(properties == null ? Map.of() : properties.getValues());
    }

    @Override
    public Optional<String> resolve(String reference) {
        if (reference == null) {
            return Optional.empty();
        }
        String key = reference.trim();
        if (key.isEmpty()) {
            return Optional.empty();
        }

        String configured = values.get(key);
        if (configured != null && !configured.isBlank()) {
            return Optional.of(configured);
        }

        String environment = System.getenv(envKey(key));
        if (environment != null && !environment.isBlank()) {
            return Optional.of(environment);
        }
        return Optional.empty();
    }

    /**
     * 将凭证引用转换为环境变量键：全大写并把非字母数字字符替换为下划线。
     */
    static String envKey(String reference) {
        Objects.requireNonNull(reference, "reference 不能为空");
        return ENV_PREFIX + reference.replaceAll("[^A-Za-z0-9]", "_").toUpperCase();
    }

    private static Map<String, String> normalize(Map<String, String> source) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (source == null) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().trim();
            if (!key.isEmpty()) {
                normalized.put(key, entry.getValue());
            }
        }
        return normalized;
    }

}
