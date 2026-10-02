package cn.iocoder.yudao.module.agent.service.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.agent.controller.admin.project.vo.project.AgentProjectPageReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.project.vo.project.AgentProjectSaveReqVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentProjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.PROJECT_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.PROJECT_NOT_FOUND;

/**
 * 代码项目资产 Service 实现类
 *
 * @author TaskForge
 */
@Service
@Validated
public class AgentProjectServiceImpl implements AgentProjectService {

    @Resource
    private AgentProjectMapper projectMapper;

    @Override
    public Long createProject(AgentProjectSaveReqVO createReqVO) {
        // 校验项目代号的唯一性
        validateProjectCodeUnique(null, createReqVO.getProjectCode());

        // 插入项目
        AgentProjectDO project = BeanUtils.toBean(createReqVO, AgentProjectDO.class);
        projectMapper.insert(project);
        return project.getId();
    }

    @Override
    public void updateProject(AgentProjectSaveReqVO updateReqVO) {
        // 校验项目存在
        validateProjectExists(updateReqVO.getId());
        // 校验项目代号的唯一性
        validateProjectCodeUnique(updateReqVO.getId(), updateReqVO.getProjectCode());

        // 更新项目
        AgentProjectDO updateObj = BeanUtils.toBean(updateReqVO, AgentProjectDO.class);
        projectMapper.updateById(updateObj);
    }

    @Override
    public void updateProjectStatus(Long id, Integer status) {
        // 校验项目存在
        validateProjectExists(id);

        // 更新状态
        AgentProjectDO updateObj = new AgentProjectDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        projectMapper.updateById(updateObj);
    }

    @Override
    public void deleteProject(Long id) {
        // 校验项目存在
        validateProjectExists(id);
        // 删除项目（软删除）
        projectMapper.deleteById(id);
    }

    @Override
    public AgentProjectDO getProject(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public PageResult<AgentProjectDO> getProjectPage(AgentProjectPageReqVO pageReqVO) {
        return projectMapper.selectPage(pageReqVO);
    }

    private void validateProjectCodeUnique(Long id, String projectCode) {
        AgentProjectDO project = projectMapper.selectByProjectCode(projectCode);
        if (project == null) {
            return;
        }
        // 创建时只要存在即冲突；更新时忽略自身
        if (id == null || !project.getId().equals(id)) {
            throw exception(PROJECT_CODE_DUPLICATE);
        }
    }

    private void validateProjectExists(Long id) {
        if (id == null) {
            return;
        }
        if (projectMapper.selectById(id) == null) {
            throw exception(PROJECT_NOT_FOUND);
        }
    }

}
