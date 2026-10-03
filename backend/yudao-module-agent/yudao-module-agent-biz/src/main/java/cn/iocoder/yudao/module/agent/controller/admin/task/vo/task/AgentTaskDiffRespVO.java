package cn.iocoder.yudao.module.agent.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 任务结果（Diff/日志/测试报告/分支）Response VO")
@Data
public class AgentTaskDiffRespVO {

    @Schema(description = "任务主键 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "9012")
    private Long taskId;

    @Schema(description = "任务唯一编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "TASK-20261001-088")
    private String taskNo;

    @Schema(description = "任务简述", requiredMode = Schema.RequiredMode.REQUIRED, example = "实现项目 CRUD")
    private String title;

    @Schema(description = "任务状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "WAITING_ACCEPTANCE")
    private String status;

    @Schema(description = "本次任务创建的特性分支", example = "feature/TASK-20261001-088")
    private String targetBranch;

    @Schema(description = "重试次数", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer retryTimes;

    @Schema(description = "总耗时（毫秒）", requiredMode = Schema.RequiredMode.REQUIRED, example = "12000")
    private Long costMs;

    @Schema(description = "开始执行时间")
    private LocalDateTime startedTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishedTime;

    @Schema(description = "执行代次", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long executionGeneration;

    @Schema(description = "任务涉及的分支信息")
    private List<AgentTaskBranchInfoVO> branches;

    @Schema(description = "Git Diff 变更统计（JSON 或原文）")
    private String diffStat;

    @Schema(description = "变更文件清单")
    private List<String> changedFiles;

    @Schema(description = "执行日志")
    private String executionLog;

    @Schema(description = "执行日志是否因过长被截断", example = "false")
    private Boolean logTruncated;

    @Schema(description = "日志截断时的原始日志路径")
    private String originalLogPath;

    @Schema(description = "测试报告（来自 .ai/workpad_summary.json）")
    private AgentTaskTestReportVO testReport;

}
