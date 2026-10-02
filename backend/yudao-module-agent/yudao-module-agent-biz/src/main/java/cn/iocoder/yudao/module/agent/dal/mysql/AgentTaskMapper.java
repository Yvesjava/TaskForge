package cn.iocoder.yudao.module.agent.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskPageReqVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 研发任务 Mapper
 *
 * @author TaskForge
 */
@Mapper
public interface AgentTaskMapper extends BaseMapperX<AgentTaskDO> {

    default PageResult<AgentTaskDO> selectPage(AgentTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AgentTaskDO>()
                .likeIfPresent(AgentTaskDO::getTaskNo, reqVO.getTaskNo())
                .likeIfPresent(AgentTaskDO::getTitle, reqVO.getTitle())
                .eqIfPresent(AgentTaskDO::getStatus, reqVO.getStatus())
                .eqIfPresent(AgentTaskDO::getPriority, reqVO.getPriority())
                .eqIfPresent(AgentTaskDO::getDependsOnTaskId, reqVO.getDependsOnTaskId())
                .eqIfPresent(AgentTaskDO::getWorkerId, reqVO.getWorkerId())
                .likeIfPresent(AgentTaskDO::getTargetBranch, reqVO.getTargetBranch())
                .orderByDesc(AgentTaskDO::getId));
    }

    /**
     * 按任务编号查询，命中唯一键 uk_task_no（自动追加租户与软删除条件）
     */
    AgentTaskDO selectByTaskNo(@Param("taskNo") String taskNo);

    /**
     * 按前置任务查询，命中索引 idx_depends_on_task
     */
    default List<AgentTaskDO> selectListByDependsOnTaskId(Long dependsOnTaskId) {
        return selectList(AgentTaskDO::getDependsOnTaskId, dependsOnTaskId);
    }

    /**
     * 调度器短事务捞取可执行任务，命中索引 idx_scheduler 并使用 FOR UPDATE SKIP LOCKED 防重复消费
     */
    List<AgentTaskDO> selectPendingTasks(@Param("limit") int limit);

    /**
     * 抢占任务：将 PENDING 置为 RUNNING 并写入租约，条件更新成功才允许继续执行
     */
    int markTaskRunning(@Param("id") Long id,
                        @Param("workerId") String workerId,
                        @Param("leaseUntil") LocalDateTime leaseUntil,
                        @Param("generation") Long generation);

    /**
     * 续租：仅允许当前 Worker 与当前执行代次更新，命中索引 idx_lease_recovery 的 status 前缀
     */
    int renewLease(@Param("id") Long id,
                   @Param("workerId") String workerId,
                   @Param("generation") Long generation,
                   @Param("leaseUntil") LocalDateTime leaseUntil);

    /**
     * 扫描租约过期的运行中任务，命中索引 idx_lease_recovery（status, lease_until）
     */
    List<AgentTaskDO> selectExpiredRunningTasks(@Param("limit") int limit);

    /**
     * 将租约过期的任务标记为 FAILED 并递增执行代次
     */
    int markLeaseExpiredFailed(@Param("id") Long id,
                               @Param("generation") Long generation);

    /**
     * 将 PAUSED 任务置为 RESETTING，防止重复重置
     */
    int markResettingIfPaused(@Param("id") Long id);

    /**
     * 完成重置：清空执行痕迹并回到 PENDING 或 PAUSED
     */
    int finishReset(@Param("id") Long id,
                    @Param("finalStatus") String finalStatus);

}
