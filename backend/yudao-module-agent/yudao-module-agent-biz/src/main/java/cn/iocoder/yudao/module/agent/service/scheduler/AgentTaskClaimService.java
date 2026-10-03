package cn.iocoder.yudao.module.agent.service.scheduler;

import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;

import java.util.Optional;

/**
 * 任务短事务抢占服务
 *
 * <p>在独立短事务内捞取并抢占下一个可执行任务；事务提交后才返回结果，
 * 调用方不得在本事务内执行 Git、Codex 等外部操作。</p>
 *
 * @author TaskForge
 */
public interface AgentTaskClaimService {

    /**
     * 抢占下一个可执行任务。
     *
     * @return 已抢占成功的任务；没有可执行任务时返回 {@link Optional#empty()}
     */
    Optional<AgentTaskDO> claimNextTask();

}
