package cn.iocoder.yudao.module.agent.controller.admin.project.vo.project;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.agent.enums.BuildToolEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 代码项目资产创建/修改 Request VO")
@Data
public class AgentProjectSaveReqVO {

    @Schema(description = "项目编号", example = "1024")
    private Long id;

    @Schema(description = "项目代号", requiredMode = Schema.RequiredMode.REQUIRED, example = "backend-service")
    @NotBlank(message = "项目代号不能为空")
    @Size(max = 64, message = "项目代号长度不能超过 64 个字符")
    private String projectCode;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "后端服务")
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 128, message = "项目名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "Git 仓库地址（SSH/HTTP）", requiredMode = Schema.RequiredMode.REQUIRED, example = "git@github.com:Yvesjava/TaskForge.git")
    @NotBlank(message = "Git 仓库地址不能为空")
    @Size(max = 255, message = "Git 仓库地址长度不能超过 255 个字符")
    @Pattern(regexp = "^(?:(?:https?|ssh)://[^\\s/]+(?:/[^\\s]*)?|git@[A-Za-z0-9._-]+:[^\\s]+)$",
            message = "Git 仓库地址格式不正确，需为 SSH 或 HTTP(S) 地址")
    private String gitUrl;

    @Schema(description = "默认主干分支", requiredMode = Schema.RequiredMode.REQUIRED, example = "main")
    @NotBlank(message = "默认主干分支不能为空")
    @Size(max = 64, message = "默认主干分支长度不能超过 64 个字符")
    @Pattern(regexp = "^(?!.*\\.\\.)(?!.*//)[A-Za-z0-9][A-Za-z0-9._/-]*(?<!\\.lock)(?<![./])$",
            message = "默认主干分支格式不正确")
    private String defaultBranch;

    @Schema(description = "构建工具：MAVEN、PNPM、GRADLE、GO", requiredMode = Schema.RequiredMode.REQUIRED, example = "MAVEN")
    @NotBlank(message = "构建工具不能为空")
    @Size(max = 32, message = "构建工具长度不能超过 32 个字符")
    @InEnum(value = BuildToolEnum.class, message = "构建工具必须在指定范围 {value}")
    private String buildTool;

    @Schema(description = "标准测试命令（如：mvn clean test）", example = "mvn clean test")
    @Size(max = 255, message = "标准测试命令长度不能超过 255 个字符")
    @Pattern(regexp = "^[^\\x00-\\x1F\\x7F]*\\S[^\\x00-\\x1F\\x7F]*$",
            message = "标准测试命令不能为空或包含控制字符")
    private String testCommand;

    @Schema(description = "Secret Manager 中的 Git 凭证引用", example = "taskforge-git")
    @Size(max = 128, message = "Git 凭证引用长度不能超过 128 个字符")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._/-]*$", message = "Git 凭证引用格式不正确")
    private String credentialRef;

    @Schema(description = "状态：0-开启，1-关闭", example = "0")
    @InEnum(CommonStatusEnum.class)
    private Integer status;

}
