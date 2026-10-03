package cn.iocoder.yudao.module.agent.framework.git.platform;

/**
 * Git 平台 HTTP 传输抽象。
 *
 * <p>抽象真实网络调用，便于在测试中注入假客户端，验证各平台算子的请求构造与响应解析。
 * 实现不得抛出异常，失败信息统一放入 {@link GitApiResponse}。</p>
 *
 * @author TaskForge
 */
@FunctionalInterface
public interface GitApiHttpClient {

    GitApiResponse exchange(GitApiRequest request);

}
