package cn.iocoder.yudao.module.agent.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 重置任务 Request VO")
@Data
public class AgentTaskResetReqVO {

    @Schema(description = "重置后是否保持暂停；true 回到 PAUSED，false（默认）回到 PENDING", example = "false")
    private Boolean keepPaused = Boolean.FALSE;

}
