package cn.iocoder.yudao.module.agent.controller.admin.task.vo.task;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - AI 研发任务分页 Request VO")
@Data
public class AgentTaskPageReqVO extends PageParam {

    @Schema(description = "任务唯一编号，模糊匹配", example = "TASK-20261001-001")
    private String taskNo;

    @Schema(description = "任务简述，模糊匹配", example = "实现项目 CRUD")
    private String title;

    @Schema(description = "任务状态", example = "PENDING")
    private String status;

    @Schema(description = "执行优先级", example = "100")
    private Integer priority;

    @Schema(description = "前置任务 ID", example = "1")
    private Long dependsOnTaskId;

    @Schema(description = "当前租约持有者", example = "worker-1")
    private String workerId;

    @Schema(description = "特性分支，模糊匹配", example = "feat/lzc-11")
    private String targetBranch;

}
