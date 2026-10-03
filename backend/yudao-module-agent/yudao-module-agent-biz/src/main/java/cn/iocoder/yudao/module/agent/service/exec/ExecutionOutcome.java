package cn.iocoder.yudao.module.agent.service.exec;

import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;

/**
 * 单次任务执行结果
 *
 * <p>承载执行后的最终状态与写回统计，便于 Worker 上报与测试断言。</p>
 *
 * @param status       状态机返回的最终状态
 * @param timedOut     是否因超时熔断
 * @param retryTimes   实际发生的自修复重试次数
 * @param costMs       本次执行总耗时（毫秒）
 * @param executionLog 已写回的完整执行日志
 */
public record ExecutionOutcome(
        AgentTaskStatus status,
        boolean timedOut,
        int retryTimes,
        long costMs,
        String executionLog) {
}
