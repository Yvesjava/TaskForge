package cn.iocoder.yudao.module.agent.framework.git.platform;

/**
 * 合并策略。
 *
 * <p>各平台实现负责将统一策略映射为平台参数；不支持的策略按平台能力做近似映射。</p>
 *
 * @author TaskForge
 */
public enum GitMergeStrategy {

    MERGE,
    SQUASH,
    REBASE,
    FAST_FORWARD

}
