package cn.iocoder.yudao.module.agent.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 卡片验收/打回回调请求 VO
 *
 * <p>回调令牌由发送端在卡片按钮 value 中写入，绑定任务编号、动作与报告版本，
 * 防止回调绕过控制面权限。</p>
 */
@Schema(description = "管理后台 - 卡片验收/打回回调 Request VO")
@Data
public class AgentNoticeCallbackReqVO {

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "TASK-20261003-001")
    private String taskNo;

    @Schema(description = "回调动作：accept/reject", requiredMode = Schema.RequiredMode.REQUIRED, example = "accept")
    private String action;

    @Schema(description = "验收报告版本", requiredMode = Schema.RequiredMode.REQUIRED, example = "v3")
    private String reportVersion;

    @Schema(description = "打回反馈（reject 时必填）", example = "实现与设计稿不一致")
    private String feedback;

    @Schema(description = "回调令牌（绑定任务编号 + 动作 + 报告版本）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String token;

}
