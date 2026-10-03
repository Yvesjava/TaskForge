package cn.iocoder.yudao.module.agent.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 投递任务文档 Request VO")
@Data
public class AgentTaskSubmitReqVO {

    @Schema(description = "完整任务 Markdown 文档（含 YAML Front Matter 与正文小节）",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "---\ntaskId: \"TASK-20261001-088\"\n...")
    @NotBlank(message = "任务文档不能为空")
    @Size(max = 200_000, message = "任务文档长度不能超过 200000 个字符")
    private String document;

}
