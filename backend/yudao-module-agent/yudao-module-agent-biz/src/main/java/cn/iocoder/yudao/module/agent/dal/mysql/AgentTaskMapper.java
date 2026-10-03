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

    /**
     * 暂停：PENDING -> PAUSED，阻止调度器继续消费
     */
    int pauseIfPending(@Param("id") Long id);

    /**
     * 恢复：PAUSED -> PENDING，回到待调度队列
     */
    int resumeIfPaused(@Param("id") Long id);

    /**
     * 取消：PENDING/PAUSED -> CANCELED，写入取消原因
     */
    int cancelIfPendingOrPaused(@Param("id") Long id,
                                @Param("cancelReason") String cancelReason);

    /**
     * 暂停任务的文档编辑：仅当状态为 PAUSED 且 docVersion 匹配时更新并递增版本。
     *
     * <p>条件更新影响行数为 0 表示版本已过期、状态非法或任务不存在。</p>
     */
    int updateDocumentIfVersionMatches(@Param("id") Long id,
                                       @Param("docVersion") Integer docVersion,
                                       @Param("document") String document,
                                       @Param("timeoutMinutes") Integer timeoutMinutes,
                                       @Param("priority") Integer priority,
                                       @Param("dependsOnTaskId") Long dependsOnTaskId);

    /**
     * 自验通过：RUNNING -> WAITING_ACCEPTANCE，释放租约并写回执行结果
     */
    int markSelfVerifiedIfRunning(@Param("id") Long id,
                                  @Param("workerId") String workerId,
                                  @Param("generation") Long generation,
                                  @Param("executionLog") String executionLog,
                                  @Param("retryTimes") Integer retryTimes,
                                  @Param("costMs") Long costMs,
                                  @Param("diffStat") String diffStat,
                                  @Param("workspacePath") String workspacePath);

    /**
     * 执行失败/超时：RUNNING -> FAILED，释放租约、写回结果并清理工作区引用
     */
    int markFailedIfRunning(@Param("id") Long id,
                            @Param("workerId") String workerId,
                            @Param("generation") Long generation,
                            @Param("executionLog") String executionLog,
                            @Param("retryTimes") Integer retryTimes,
                            @Param("costMs") Long costMs,
                            @Param("diffStat") String diffStat);

    /**
     * 验收通过：WAITING_ACCEPTANCE -> ACCEPTED
     */
    int acceptIfWaitingAcceptance(@Param("id") Long id);

    /**
     * 打回：WAITING_ACCEPTANCE -> REJECTED
     */
    int rejectIfWaitingAcceptance(@Param("id") Long id);

    /**
     * 合并成功：ACCEPTED -> COMPLETED
     */
    int completeIfAccepted(@Param("id") Long id);

    /**
     * 合并冲突：ACCEPTED -> MERGE_CONFLICT_PENDING_MANUAL
     */
    int markMergeConflictIfAccepted(@Param("id") Long id);

    /**
     * 重新入队：REJECTED/FAILED/CANCELED -> PENDING，生成新的执行代次
     */
    int reEnqueueIfEnded(@Param("id") Long id);

    /**
     * 软删除：CANCELED -> DELETED（deleted=1）
     */
    int softDeleteIfCanceled(@Param("id") Long id);

}
