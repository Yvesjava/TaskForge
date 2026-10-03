package cn.iocoder.yudao.module.agent.service.ops;

/**
 * 补偿任务服务
 *
 * <p>统一编排残留工作区、过期租约与失败通知三类补偿，所有补偿均幂等可重复执行，
 * 并在完成恢复或仍失败时写入告警记录。</p>
 *
 * @author TaskForge
 */
public interface AgentCompensationService {

    /**
     * 执行一轮补偿。
     *
     * @return 本轮补偿结果汇总
     */
    AgentCompensationResult compensate();

}
