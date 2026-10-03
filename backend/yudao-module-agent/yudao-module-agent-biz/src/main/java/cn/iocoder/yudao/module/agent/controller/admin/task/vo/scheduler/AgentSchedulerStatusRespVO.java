package cn.iocoder.yudao.module.agent.controller.admin.task.vo.scheduler;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调度器运行状态 Response VO
 *
 * @author TaskForge
 */
@Schema(description = "管理后台 - 调度器运行状态 Response VO")
@Data
public class AgentSchedulerStatusRespVO {

    @Schema(description = "排队中任务数（PENDING）", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    private Long queueLength;

    @Schema(description = "运行中任务数（RUNNING）", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Long runningCount;

    @Schema(description = "租约异常任务数（RUNNING 且租约已过期）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long leaseAnomalyCount;

    @Schema(description = "状态检查时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime checkedAt;

}
