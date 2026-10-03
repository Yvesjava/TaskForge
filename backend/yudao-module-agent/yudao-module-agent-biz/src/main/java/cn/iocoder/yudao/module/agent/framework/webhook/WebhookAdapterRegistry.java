package cn.iocoder.yudao.module.agent.framework.webhook;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 通知 Webhook 适配器注册表
 *
 * <p>Spring 容器自动收集全部 {@link WebhookAdapter}，按平台路由。业务侧通过
 * {@link #get(WebhookPlatform)} 取得对应适配器，无需感知具体实现。</p>
 *
 * @author TaskForge
 */
@Component
public class WebhookAdapterRegistry {

    private final Map<WebhookPlatform, WebhookAdapter> adapters;

    public WebhookAdapterRegistry(List<WebhookAdapter> adapterList) {
        Map<WebhookPlatform, WebhookAdapter> resolved = new EnumMap<>(WebhookPlatform.class);
        for (WebhookAdapter adapter : adapterList) {
            Objects.requireNonNull(adapter.platform(), "adapter.platform 不能为空");
            WebhookAdapter previous = resolved.put(adapter.platform(), adapter);
            if (previous != null) {
                throw new IllegalStateException("存在重复的 Webhook 适配器：" + adapter.platform());
            }
        }
        this.adapters = Map.copyOf(resolved);
    }

    public WebhookAdapter get(WebhookPlatform platform) {
        WebhookAdapter adapter = adapters.get(platform);
        if (adapter == null) {
            throw new IllegalArgumentException("不支持的 Webhook 平台：" + platform);
        }
        return adapter;
    }

}
