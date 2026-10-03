package cn.iocoder.yudao.module.agent.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - AI 研发任务信息 Response VO")
@Data
public class AgentTaskRespVO {

    @Schema(description = "任务主键 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "9012")
    private Long id;

    @Schema(description = "任务唯一编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "TASK-20261001-088")
    private String taskNo;

    @Schema(description = "任务简述", requiredMode = Schema.RequiredMode.REQUIRED, example = "实现项目 CRUD")
    private String title;

    @Schema(description = "任务状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "PENDING")
    private String status;

    @Schema(description = "任务需求文档（含计划与验收标准）", example = "---\ntaskId: \"TASK-20261001-088\"\n...")
    private String taskDoc;

    @Schema(description = "执行优先级（数值越小越优先）", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Integer priority;

    @Schema(description = "文档版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer docVersion;

    @Schema(description = "前置任务 ID", example = "9001")
    private Long dependsOnTaskId;

    @Schema(description = "宿主机物理工作区绝对路径", example = "/data/agent-workspace/dirA-TASK-20261001-088")
    private String workspacePath;

    @Schema(description = "本次任务创建的特性分支", example = "feat/TASK-20261001-088")
    private String targetBranch;

    @Schema(description = "超时时长（分钟）", requiredMode = Schema.RequiredMode.REQUIRED, example = "30")
    private Integer timeoutMinutes;

    @Schema(description = "取消原因", example = "需求变更")
    private String cancelReason;

    @Schema(description = "重试次数", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer retryTimes;

    @Schema(description = "总耗时（毫秒）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Long costMs;

    @Schema(description = "当前租约持有者", example = "worker-1")
    private String workerId;

    @Schema(description = "Worker 租约到期时间")
    private LocalDateTime leaseUntil;

    @Schema(description = "执行代次", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Long executionGeneration;

    @Schema(description = "最近一次 Worker 心跳时间")
    private LocalDateTime heartbeatTime;

    @Schema(description = "开始执行时间")
    private LocalDateTime startedTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishedTime;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
