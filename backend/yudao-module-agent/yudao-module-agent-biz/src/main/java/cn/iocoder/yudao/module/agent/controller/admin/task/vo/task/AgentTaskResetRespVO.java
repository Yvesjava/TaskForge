package cn.iocoder.yudao.module.agent.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 重置任务 Response VO")
@Data
public class AgentTaskResetRespVO {

    @Schema(description = "任务主键 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "9012")
    private Long taskId;

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "TASK-20261001-088")
    private String taskNo;

    @Schema(description = "重置后最终状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "PENDING")
    private String status;

}
