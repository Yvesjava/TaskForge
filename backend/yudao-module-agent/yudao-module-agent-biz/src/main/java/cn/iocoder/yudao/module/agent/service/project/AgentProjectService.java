package cn.iocoder.yudao.module.agent.service.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.agent.controller.admin.project.vo.project.AgentProjectPageReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.project.vo.project.AgentProjectSaveReqVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentProjectDO;

/**
 * 代码项目资产 Service 接口
 *
 * @author TaskForge
 */
public interface AgentProjectService {

    /**
     * 创建项目资产
     *
     * @param createReqVO 项目信息
     * @return 项目编号
     */
    Long createProject(AgentProjectSaveReqVO createReqVO);

    /**
     * 更新项目资产
     *
     * @param updateReqVO 项目信息
     */
    void updateProject(AgentProjectSaveReqVO updateReqVO);

    /**
     * 启停项目资产
     *
     * @param id     项目编号
     * @param status 状态：0-开启，1-关闭
     */
    void updateProjectStatus(Long id, Integer status);

    /**
     * 软删除项目资产
     *
     * @param id 项目编号
     */
    void deleteProject(Long id);

    /**
     * 获得项目资产
     *
     * @param id 项目编号
     * @return 项目信息，不存在时返回 null
     */
    AgentProjectDO getProject(Long id);

    /**
     * 获得项目资产分页
     *
     * @param pageReqVO 分页条件
     * @return 项目分页列表
     */
    PageResult<AgentProjectDO> getProjectPage(AgentProjectPageReqVO pageReqVO);

}
