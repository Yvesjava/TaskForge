package cn.iocoder.yudao.module.agent.service.ops;

/**
 * 失败通知补偿单轮投递结果。
 *
 * @param attempted 本轮处理的通知条数
 * @param sent      投递成功条数
 * @param failed    仍失败条数
 * @author TaskForge
 */
public record AgentNoticeDispatchResult(int attempted, int sent, int failed) {

    public AgentNoticeDispatchResult {
        if (attempted < 0 || sent < 0 || failed < 0) {
            throw new IllegalArgumentException("投递统计不能为负数");
        }
    }

}
