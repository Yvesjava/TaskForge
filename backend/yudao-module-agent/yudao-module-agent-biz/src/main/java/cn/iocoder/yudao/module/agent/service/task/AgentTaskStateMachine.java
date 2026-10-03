package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;

/**
 * 任务状态机统一服务入口
 *
 * <p>任务状态只能通过本服务变更：先校验合法转换矩阵，再执行带状态前置条件的
 * 条件更新 SQL，并在同一事务内写入操作审计。控制器、调度器、Worker 与回调不得
 * 直接更新 {@code agent_task.status}。</p>
 *
 * @author TaskForge
 */
public interface AgentTaskStateMachine {

    /**
     * 执行一次状态转换
     *
     * @param command 状态转换命令
     * @return 转换后的状态
     */
    AgentTaskStatus transition(AgentTaskTransitionCommand command);

}
