package cn.iocoder.yudao.module.agent.service.ops;

/**
 * 一轮补偿任务执行结果汇总。
 *
 * @param residualWorkspacesCleaned 清理的残留工作区数量
 * @param expiredLeasesRecovered    恢复的过期租约数量
 * @param noticesSent               补偿成功的失败通知数量
 * @param noticesFailed             补偿后仍失败的通知数量
 * @author TaskForge
 */
public record AgentCompensationResult(int residualWorkspacesCleaned,
                                      int expiredLeasesRecovered,
                                      int noticesSent,
                                      int noticesFailed) {

    public boolean hasWork() {
        return residualWorkspacesCleaned > 0
                || expiredLeasesRecovered > 0
                || noticesSent > 0
                || noticesFailed > 0;
    }

}
