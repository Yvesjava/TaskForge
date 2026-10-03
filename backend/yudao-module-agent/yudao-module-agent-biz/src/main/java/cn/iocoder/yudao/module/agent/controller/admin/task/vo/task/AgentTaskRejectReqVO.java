package cn.iocoder.yudao.module.agent.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 打回任务 Request VO")
@Data
public class AgentTaskRejectReqVO {

    @Schema(description = "打回反馈", requiredMode = Schema.RequiredMode.REQUIRED, example = "前端页面样式与设计稿不一致")
    @NotBlank(message = "打回反馈不能为空")
    @Size(max = 2000, message = "打回反馈长度不能超过 2000 个字符")
    private String feedback;

}
