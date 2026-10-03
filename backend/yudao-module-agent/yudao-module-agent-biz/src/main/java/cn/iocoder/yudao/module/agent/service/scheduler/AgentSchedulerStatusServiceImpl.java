package cn.iocoder.yudao.module.agent.service.scheduler;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.scheduler.AgentSchedulerStatusRespVO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 调度器运行状态查询服务实现
 *
 * <p>调度器按租户忽略模式扫描所有租户的可执行任务，因此这里的运行状态指标同样
 * 跨租户聚合，与调度器的全局视角保持一致。</p>
 *
 * @author TaskForge
 */
@Service
public class AgentSchedulerStatusServiceImpl implements AgentSchedulerStatusService {

    @Resource
    private AgentTaskMapper taskMapper;

    @Override
    public AgentSchedulerStatusRespVO getStatus() {
        LocalDateTime checkedAt = LocalDateTime.now();
        return TenantUtils.executeIgnore(() -> {
            AgentSchedulerStatusRespVO respVO = new AgentSchedulerStatusRespVO();
            respVO.setQueueLength(taskMapper.countPendingTasks());
            respVO.setRunningCount(taskMapper.countRunningTasks());
            respVO.setLeaseAnomalyCount(taskMapper.countLeaseAnomalies(checkedAt));
            respVO.setCheckedAt(checkedAt);
            return respVO;
        });
    }

}
