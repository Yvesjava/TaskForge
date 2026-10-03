package cn.iocoder.yudao.module.agent.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskSubmitReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskSubmitRespVO;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 任务控制面")
@RestController
@RequestMapping("/agent/task")
@Validated
public class AgentTaskController {

    @Resource
    private AgentTaskService taskService;

    @PostMapping("/submit")
    @Operation(summary = "投递任务文档")
    @PreAuthorize("@ss.hasPermission('agent:task:submit')")
    public CommonResult<AgentTaskSubmitRespVO> submit(
            @Valid @RequestBody AgentTaskSubmitReqVO reqVO,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return success(taskService.submit(reqVO.getDocument(), idempotencyKey));
    }

}
