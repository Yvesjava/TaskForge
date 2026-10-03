package cn.iocoder.yudao.module.agent.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 投递任务文档 Response VO")
@Data
public class AgentTaskSubmitRespVO {

    @Schema(description = "任务主键 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "9012")
    private Long taskId;

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "TASK-20261001-088")
    private String taskNo;

    @Schema(description = "当前状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "PENDING")
    private String status;

    @Schema(description = "文档版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer docVersion;

    @Schema(description = "执行代次", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Long executionGeneration;

    @Schema(description = "操作记录 ID（幂等审计追踪）", requiredMode = Schema.RequiredMode.REQUIRED, example = "op_1")
    private String operationId;

}
