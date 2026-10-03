package cn.iocoder.yudao.module.agent.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 编辑暂停任务文档 Request VO")
@Data
public class AgentTaskUpdateDocumentReqVO {

    @Schema(description = "当前文档版本号（乐观锁）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "文档版本号不能为空")
    @Min(value = 1, message = "文档版本号最小为 1")
    private Integer docVersion;

    @Schema(description = "完整任务 Markdown 文档（含 YAML Front Matter 与正文小节）",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "---\ntaskId: \"TASK-20261001-088\"\n...")
    @NotBlank(message = "任务文档不能为空")
    @Size(max = 200_000, message = "任务文档长度不能超过 200000 个字符")
    private String document;

    @Schema(description = "超时时长（分钟）", example = "45")
    @Min(value = 1, message = "超时时长最小为 1 分钟")
    @Max(value = 1440, message = "超时时长最大为 1440 分钟")
    private Integer timeoutMinutes;

    @Schema(description = "执行优先级（数值越小越优先）", example = "100")
    @Min(value = 0, message = "优先级最小为 0")
    @Max(value = 1000, message = "优先级最大为 1000")
    private Integer priority;

    @Schema(description = "可选的前置任务 ID", example = "9001")
    private Long dependsOnTaskId;

}
