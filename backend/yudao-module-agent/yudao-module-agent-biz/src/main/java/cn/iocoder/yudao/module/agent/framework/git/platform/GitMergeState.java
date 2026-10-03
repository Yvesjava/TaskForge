package cn.iocoder.yudao.module.agent.framework.git.platform;

/**
 * 合并请求（MR/PR）平台侧状态。
 *
 * @author TaskForge
 */
public enum GitMergeState {

    OPEN,
    MERGED,
    CLOSED,
    LOCKED,
    UNKNOWN

}
