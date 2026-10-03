package cn.iocoder.yudao.module.agent.service.scheduler;

import cn.iocoder.yudao.module.agent.controller.admin.task.vo.scheduler.AgentSchedulerStatusRespVO;

/**
 * 调度器运行状态查询服务
 *
 * @author TaskForge
 */
public interface AgentSchedulerStatusService {

    /**
     * 查询调度器当前运行状态指标：队列长度、运行中任务数、租约异常任务数。
     *
     * @return 调度器运行状态指标
     */
    AgentSchedulerStatusRespVO getStatus();

}
