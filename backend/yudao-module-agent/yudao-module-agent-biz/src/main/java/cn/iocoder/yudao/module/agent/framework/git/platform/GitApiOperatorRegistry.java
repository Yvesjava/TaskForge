package cn.iocoder.yudao.module.agent.framework.git.platform;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Git 平台算子注册表。
 *
 * <p>Spring 容器自动收集全部 {@link GitApiOperator}，按平台路由。业务侧通过
 * {@link #get(GitPlatform)} 取得对应算子。</p>
 *
 * @author TaskForge
 */
@Component
public class GitApiOperatorRegistry {

    private final Map<GitPlatform, GitApiOperator> operators;

    public GitApiOperatorRegistry(List<GitApiOperator> operatorList) {
        Map<GitPlatform, GitApiOperator> resolved = new EnumMap<>(GitPlatform.class);
        for (GitApiOperator operator : operatorList) {
            Objects.requireNonNull(operator.platform(), "operator.platform 不能为空");
            GitApiOperator previous = resolved.put(operator.platform(), operator);
            if (previous != null) {
                throw new IllegalStateException("存在重复的 Git 平台算子：" + operator.platform());
            }
        }
        this.operators = Map.copyOf(resolved);
    }

    public GitApiOperator get(GitPlatform platform) {
        GitApiOperator operator = operators.get(platform);
        if (operator == null) {
            throw new IllegalArgumentException("不支持的 Git 平台：" + platform);
        }
        return operator;
    }

}
