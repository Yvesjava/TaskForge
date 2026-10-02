package cn.iocoder.yudao.module.agent.controller.admin.project;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.agent.controller.admin.project.vo.project.AgentProjectPageReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.project.vo.project.AgentProjectRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.project.vo.project.AgentProjectSaveReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.project.vo.project.AgentProjectUpdateStatusReqVO;
import cn.iocoder.yudao.module.agent.service.project.AgentProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目资产")
@RestController
@RequestMapping("/agent/project")
@Validated
public class AgentProjectController {

    @Resource
    private AgentProjectService projectService;

    @PostMapping("/create")
    @Operation(summary = "创建项目资产")
    @PreAuthorize("@ss.hasPermission('agent:project:create')")
    public CommonResult<Long> createProject(@Valid @RequestBody AgentProjectSaveReqVO createReqVO) {
        return success(projectService.createProject(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改项目资产")
    @PreAuthorize("@ss.hasPermission('agent:project:update')")
    public CommonResult<Boolean> updateProject(@Valid @RequestBody AgentProjectSaveReqVO updateReqVO) {
        projectService.updateProject(updateReqVO);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "启停项目资产")
    @PreAuthorize("@ss.hasPermission('agent:project:update')")
    public CommonResult<Boolean> updateProjectStatus(@Valid @RequestBody AgentProjectUpdateStatusReqVO reqVO) {
        projectService.updateProjectStatus(reqVO.getId(), reqVO.getStatus());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "软删除项目资产")
    @Parameter(name = "id", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('agent:project:delete')")
    public CommonResult<Boolean> deleteProject(@RequestParam("id") Long id) {
        projectService.deleteProject(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得项目资产")
    @Parameter(name = "id", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('agent:project:query')")
    public CommonResult<AgentProjectRespVO> getProject(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(projectService.getProject(id), AgentProjectRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得项目资产分页")
    @PreAuthorize("@ss.hasPermission('agent:project:query')")
    public CommonResult<PageResult<AgentProjectRespVO>> getProjectPage(@Validated AgentProjectPageReqVO pageReqVO) {
        return success(BeanUtils.toBean(projectService.getProjectPage(pageReqVO), AgentProjectRespVO.class));
    }

}
