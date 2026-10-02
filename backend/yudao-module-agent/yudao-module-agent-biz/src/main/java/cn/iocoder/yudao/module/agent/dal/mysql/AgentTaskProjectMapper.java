package cn.iocoder.yudao.module.agent.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.project.AgentTaskProjectPageReqVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskProjectDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务-项目关联 Mapper
 *
 * @author TaskForge
 */
@Mapper
public interface AgentTaskProjectMapper extends BaseMapperX<AgentTaskProjectDO> {

    default PageResult<AgentTaskProjectDO> selectPage(AgentTaskProjectPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AgentTaskProjectDO>()
                .eqIfPresent(AgentTaskProjectDO::getTaskId, reqVO.getTaskId())
                .eqIfPresent(AgentTaskProjectDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(AgentTaskProjectDO::getProjectCode, reqVO.getProjectCode())
                .eqIfPresent(AgentTaskProjectDO::getBaseBranch, reqVO.getBaseBranch())
                .likeIfPresent(AgentTaskProjectDO::getSubDir, reqVO.getSubDir())
                .eqIfPresent(AgentTaskProjectDO::getMergeStatus, reqVO.getMergeStatus())
                .orderByDesc(AgentTaskProjectDO::getId));
    }

    /**
     * 按任务 ID 查询项目映射，命中索引 idx_task_id
     */
    List<AgentTaskProjectDO> selectListByTaskId(@Param("taskId") Long taskId);

}
