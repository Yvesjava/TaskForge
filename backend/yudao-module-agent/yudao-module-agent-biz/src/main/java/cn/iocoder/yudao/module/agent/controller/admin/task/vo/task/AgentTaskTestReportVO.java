package cn.iocoder.yudao.module.agent.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 任务测试报告 VO（对应 .ai/workpad_summary.json）")
@Data
public class AgentTaskTestReportVO {

    @Schema(description = "所有测试是否通过", example = "true")
    private Boolean allPassed;

    @Schema(description = "执行的测试命令")
    private List<String> testsExecuted;

    @Schema(description = "修改的文件")
    private List<String> modifiedFiles;

    @Schema(description = "备注")
    private String notes;

}
