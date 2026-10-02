package cn.iocoder.yudao.module.agent.controller.admin.project.vo.project;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 代码项目资产分页 Request VO")
@Data
public class AgentProjectPageReqVO extends PageParam {

    @Schema(description = "项目代号，模糊匹配", example = "backend-service")
    private String projectCode;

    @Schema(description = "项目名称，模糊匹配", example = "后端服务")
    private String name;

    @Schema(description = "Git 仓库地址，模糊匹配", example = "git@github.com:Yvesjava/TaskForge.git")
    private String gitUrl;

    @Schema(description = "构建工具", example = "MAVEN")
    private String buildTool;

    @Schema(description = "状态：0-开启，1-关闭", example = "0")
    private Integer status;

}
