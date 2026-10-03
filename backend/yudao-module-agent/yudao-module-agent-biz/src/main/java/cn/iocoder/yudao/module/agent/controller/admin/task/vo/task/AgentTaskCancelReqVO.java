package cn.iocoder.yudao.module.agent.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 取消任务 Request VO")
@Data
public class AgentTaskCancelReqVO {

    @Schema(description = "取消原因", example = "需求变更")
    @Size(max = 2000, message = "取消原因长度不能超过 2000 个字符")
    private String cancelReason;

}
