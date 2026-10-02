package cn.iocoder.yudao.module.agent.controller.admin.project.vo.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 代码项目资产信息 Response VO")
@Data
public class AgentProjectRespVO {

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "项目代号", requiredMode = Schema.RequiredMode.REQUIRED, example = "backend-service")
    private String projectCode;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "后端服务")
    private String name;

    @Schema(description = "Git 仓库地址（SSH/HTTP）", requiredMode = Schema.RequiredMode.REQUIRED, example = "git@github.com:Yvesjava/TaskForge.git")
    private String gitUrl;

    @Schema(description = "默认主干分支", requiredMode = Schema.RequiredMode.REQUIRED, example = "main")
    private String defaultBranch;

    @Schema(description = "构建工具：MAVEN、PNPM、GRADLE、GO", requiredMode = Schema.RequiredMode.REQUIRED, example = "MAVEN")
    private String buildTool;

    @Schema(description = "标准测试命令（如：mvn clean test）", example = "mvn clean test")
    private String testCommand;

    @Schema(description = "Secret Manager 中的 Git 凭证引用", example = "taskforge-git")
    private String credentialRef;

    @Schema(description = "状态：0-开启，1-关闭", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
