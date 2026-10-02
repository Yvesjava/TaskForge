package cn.iocoder.yudao.module.agent.controller.admin.task.vo.operationlog;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 任务操作审计日志分页 Request VO")
@Data
public class AgentTaskOperationLogPageReqVO extends PageParam {

    @Schema(description = "任务 ID", example = "1")
    private Long taskId;

    @Schema(description = "操作类型", example = "SUBMIT")
    private String action;

    @Schema(description = "操作人 ID", example = "1")
    private Long operatorId;

    @Schema(description = "请求幂等键", example = "submit-20261001-001")
    private String requestIdempotencyKey;

    @Schema(description = "操作前状态", example = "PENDING")
    private String fromStatus;

    @Schema(description = "操作后状态", example = "RUNNING")
    private String toStatus;

    @Schema(description = "操作时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

    @Schema(description = "租户编号（由服务层从租户上下文填充）", example = "1")
    private Long tenantId;

}
