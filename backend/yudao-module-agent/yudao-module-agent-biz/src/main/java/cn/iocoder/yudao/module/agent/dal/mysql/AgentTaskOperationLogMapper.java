package cn.iocoder.yudao.module.agent.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.operationlog.AgentTaskOperationLogPageReqVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务操作审计日志 Mapper
 *
 * @author TaskForge
 */
@Mapper
public interface AgentTaskOperationLogMapper extends BaseMapperX<AgentTaskOperationLogDO> {

    default PageResult<AgentTaskOperationLogDO> selectPage(AgentTaskOperationLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AgentTaskOperationLogDO>()
                .eqIfPresent(AgentTaskOperationLogDO::getTaskId, reqVO.getTaskId())
                .eqIfPresent(AgentTaskOperationLogDO::getAction, reqVO.getAction())
                .eqIfPresent(AgentTaskOperationLogDO::getOperatorId, reqVO.getOperatorId())
                .eqIfPresent(AgentTaskOperationLogDO::getRequestIdempotencyKey, reqVO.getRequestIdempotencyKey())
                .eqIfPresent(AgentTaskOperationLogDO::getFromStatus, reqVO.getFromStatus())
                .eqIfPresent(AgentTaskOperationLogDO::getToStatus, reqVO.getToStatus())
                .eqIfPresent(AgentTaskOperationLogDO::getTenantId, reqVO.getTenantId())
                .betweenIfPresent(AgentTaskOperationLogDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(AgentTaskOperationLogDO::getId));
    }

    /**
     * 按任务 ID 查询操作日志，命中索引 idx_task_id
     */
    List<AgentTaskOperationLogDO> selectListByTaskId(@Param("taskId") Long taskId);

    /**
     * 按任务 ID + 操作时间区间查询，命中索引 idx_task_time
     */
    List<AgentTaskOperationLogDO> selectListByTaskIdAndCreateTime(@Param("taskId") Long taskId,
                                                                  @Param("beginTime") LocalDateTime beginTime,
                                                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 幂等查询：按任务 ID + 请求幂等键查询，命中唯一键 uk_task_request
     */
    AgentTaskOperationLogDO selectByTaskIdAndRequestKey(@Param("taskId") Long taskId,
                                                        @Param("requestIdempotencyKey") String requestIdempotencyKey);

}
