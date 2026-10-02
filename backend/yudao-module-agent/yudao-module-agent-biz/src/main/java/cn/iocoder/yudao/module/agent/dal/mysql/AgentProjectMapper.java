package cn.iocoder.yudao.module.agent.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.agent.controller.admin.project.vo.project.AgentProjectPageReqVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentProjectDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 代码项目资产 Mapper
 *
 * @author TaskForge
 */
@Mapper
public interface AgentProjectMapper extends BaseMapperX<AgentProjectDO> {

    default PageResult<AgentProjectDO> selectPage(AgentProjectPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AgentProjectDO>()
                .likeIfPresent(AgentProjectDO::getProjectCode, reqVO.getProjectCode())
                .likeIfPresent(AgentProjectDO::getName, reqVO.getName())
                .likeIfPresent(AgentProjectDO::getGitUrl, reqVO.getGitUrl())
                .eqIfPresent(AgentProjectDO::getBuildTool, reqVO.getBuildTool())
                .eqIfPresent(AgentProjectDO::getStatus, reqVO.getStatus())
                .orderByDesc(AgentProjectDO::getId));
    }

    /**
     * 按项目代号查询，命中唯一键 uk_project_code（自动追加租户与软删除条件）
     */
    AgentProjectDO selectByProjectCode(@Param("projectCode") String projectCode);

}
