package cn.iocoder.yudao.module.agent.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskCancelReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskOperationRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskRejectReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskSubmitReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskSubmitRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskUpdateDocumentReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskUpdateDocumentRespVO;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @PatchMapping("/{id}/document")
    @Operation(summary = "编辑暂停任务的文档")
    @PreAuthorize("@ss.hasPermission('agent:task:update')")
    public CommonResult<AgentTaskUpdateDocumentRespVO> updateDocument(
            @PathVariable("id") Long id,
            @Valid @RequestBody AgentTaskUpdateDocumentReqVO reqVO,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return success(taskService.updateDocument(id, reqVO, idempotencyKey, ifMatch));
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "暂停任务")
    @PreAuthorize("@ss.hasPermission('agent:task:pause')")
    public CommonResult<AgentTaskOperationRespVO> pause(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return success(taskService.pause(id, idempotencyKey));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "恢复任务排队")
    @PreAuthorize("@ss.hasPermission('agent:task:resume')")
    public CommonResult<AgentTaskOperationRespVO> resume(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return success(taskService.resume(id, idempotencyKey));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消任务")
    @PreAuthorize("@ss.hasPermission('agent:task:cancel')")
    public CommonResult<AgentTaskOperationRespVO> cancel(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) AgentTaskCancelReqVO reqVO,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return success(taskService.cancel(id, reqVO == null ? null : reqVO.getCancelReason(), idempotencyKey));
    }

    @PostMapping("/{id}/re-enqueue")
    @Operation(summary = "重新入队任务")
    @PreAuthorize("@ss.hasPermission('agent:task:re-enqueue')")
    public CommonResult<AgentTaskOperationRespVO> reEnqueue(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return success(taskService.reEnqueue(id, idempotencyKey));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删除任务")
    @PreAuthorize("@ss.hasPermission('agent:task:delete')")
    public CommonResult<AgentTaskOperationRespVO> deleteTask(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return success(taskService.deleteTask(id, idempotencyKey));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "验收通过")
    @PreAuthorize("@ss.hasPermission('agent:task:accept')")
    public CommonResult<AgentTaskOperationRespVO> accept(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return success(taskService.accept(id, idempotencyKey));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "打回任务")
    @PreAuthorize("@ss.hasPermission('agent:task:reject')")
    public CommonResult<AgentTaskOperationRespVO> reject(
            @PathVariable("id") Long id,
            @Valid @RequestBody AgentTaskRejectReqVO reqVO,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return success(taskService.reject(id, reqVO.getFeedback(), idempotencyKey));
    }

    @PostMapping("/{id}/merge-conflict")
    @Operation(summary = "合并冲突转人工处理")
    @PreAuthorize("@ss.hasPermission('agent:task:merge-conflict')")
    public CommonResult<AgentTaskOperationRespVO> markMergeConflict(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return success(taskService.markMergeConflict(id, idempotencyKey));
    }

}
