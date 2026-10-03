package cn.iocoder.yudao.module.agent.framework.secret;

import java.util.Optional;

/**
 * 凭证管理器抽象
 *
 * <p>凭证（Git Token、SSH 私钥、密码、Webhook 密钥等）只允许保存在部署环境的
 * Secret Manager 或加密配置中心，数据库仅保存 {@code credential_ref} 引用。
 * 业务代码通过本接口按引用读取短期凭证，禁止把解析出的秘密写入日志、卡片或
 * 错误响应。</p>
 *
 * @author TaskForge
 */
public interface SecretManager {

    /**
     * 按引用解析凭证。
     *
     * @param reference 凭证引用（如 {@code taskforge-git}、{@code github.com/org}）
     * @return 解析到的凭证；引用为空、未配置或无法解析时返回 {@link Optional#empty()}
     */
    Optional<String> resolve(String reference);

}
