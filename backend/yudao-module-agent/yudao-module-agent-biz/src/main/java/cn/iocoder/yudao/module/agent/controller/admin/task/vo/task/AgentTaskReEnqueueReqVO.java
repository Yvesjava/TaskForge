package cn.iocoder.yudao.module.agent.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 重新入队任务 Request VO")
@Data
public class AgentTaskReEnqueueReqVO {

    @Schema(description = "是否克隆为全新任务编号，true 表示克隆重投，false/缺省表示原位重投", example = "false")
    private Boolean clone;

}
