package cn.iocoder.yudao.module.agent.framework.git.platform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 单次 Git 平台 API 请求。
 *
 * @param method  HTTP 方法（统一为大写）
 * @param url     请求地址
 * @param headers 请求头
 * @param body    请求体（可为空）
 * @author TaskForge
 */
public record GitApiRequest(String method, String url, Map<String, String> headers, String body) {

    public GitApiRequest {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("HTTP 方法不能为空");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("请求地址不能为空");
        }
        method = method.toUpperCase(Locale.ROOT);
        headers = headers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        body = body == null ? "" : body;
    }

    public static GitApiRequest of(String method, String url, Map<String, String> headers, String body) {
        return new GitApiRequest(method, url, headers, body);
    }

}
