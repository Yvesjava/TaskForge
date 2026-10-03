package cn.iocoder.yudao.module.agent.service.merge;

/**
 * 合并预检单项失败原因。
 *
 * @author TaskForge
 */
public enum MergePrecheckFailure {

    /**
     * 平台判定目标分支存在冲突（不可无冲突合并）。
     */
    CONFLICT,

    /**
     * 平台未返回可合并性，无法确认可以安全合并。
     */
    CHECK_FAILED,

    /**
     * 平台接口调用失败或平台算子缺失。
     */
    ERROR

}
