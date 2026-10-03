package cn.iocoder.yudao.module.agent.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 任务分支信息 VO")
@Data
public class AgentTaskBranchInfoVO {

    @Schema(description = "项目代号", requiredMode = Schema.RequiredMode.REQUIRED, example = "backend-service")
    private String projectCode;

    @Schema(description = "项目名称", example = "后端服务")
    private String projectName;

    @Schema(description = "检出基线分支", example = "main")
    private String baseBranch;

    @Schema(description = "任务特性分支", example = "feature/TASK-20261001-088")
    private String featureBranch;

    @Schema(description = "分支 Web 地址", example = "https://github.com/org/repo/tree/feature/TASK-20261001-088")
    private String branchUrl;

    @Schema(description = "在聚合工作区下的子目录名", example = "backend")
    private String subDir;

    @Schema(description = "合并状态", example = "UNMERGED")
    private String mergeStatus;

    @Schema(description = "最终提交 CommitId", example = "7bdde33bc")
    private String commitHash;

}
