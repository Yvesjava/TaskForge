package cn.iocoder.yudao.module.agent.controller.admin.task.vo.project;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 任务-项目关联分页 Request VO")
@Data
public class AgentTaskProjectPageReqVO extends PageParam {

    @Schema(description = "任务 ID", example = "1")
    private Long taskId;

    @Schema(description = "项目 ID", example = "1")
    private Long projectId;

    @Schema(description = "项目代号", example = "backend-service")
    private String projectCode;

    @Schema(description = "检出基线分支", example = "main")
    private String baseBranch;

    @Schema(description = "子目录名，模糊匹配", example = "backend")
    private String subDir;

    @Schema(description = "合并状态", example = "UNMERGED")
    private String mergeStatus;

}
