package cn.iocoder.yudao.module.agent.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.scheduler.AgentSchedulerStatusRespVO;
import cn.iocoder.yudao.module.agent.service.scheduler.AgentSchedulerStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 调度器运行状态")
@RestController
@RequestMapping("/agent/task/scheduler")
@Validated
@TenantIgnore
public class AgentTaskSchedulerController {

    @Resource
    private AgentSchedulerStatusService schedulerStatusService;

    @GetMapping("/status")
    @Operation(summary = "查询调度器运行状态")
    @PermitAll
    public CommonResult<AgentSchedulerStatusRespVO> status() {
        return success(schedulerStatusService.getStatus());
    }

}
