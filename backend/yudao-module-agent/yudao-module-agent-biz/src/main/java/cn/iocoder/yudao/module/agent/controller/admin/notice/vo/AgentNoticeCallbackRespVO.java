package cn.iocoder.yudao.module.agent.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 卡片验收/打回回调响应 VO
 *
 * <p>通过 {@code success} 与 {@code retryable} 两个字段向调用方表达本次回调是否
 * 已落地以及失败时是否值得重试，避免卡片平台对永久失败无限重试。</p>
 */
@Schema(description = "管理后台 - 卡片验收/打回回调 Response VO")
@Data
public class AgentNoticeCallbackRespVO {

    @Schema(description = "是否处理成功（含幂等命中）")
    private boolean success;

    @Schema(description = "失败时是否可重试")
    private boolean retryable;

    @Schema(description = "任务编号")
    private String taskNo;

    @Schema(description = "当前任务状态")
    private String status;

    @Schema(description = "操作记录 ID（幂等审计追踪）")
    private String operationId;

    @Schema(description = "处理说明")
    private String message;

}
