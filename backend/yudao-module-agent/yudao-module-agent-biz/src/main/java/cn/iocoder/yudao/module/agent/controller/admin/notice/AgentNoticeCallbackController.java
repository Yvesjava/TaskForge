package cn.iocoder.yudao.module.agent.controller.admin.notice;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.agent.controller.admin.notice.vo.AgentNoticeCallbackReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.notice.vo.AgentNoticeCallbackRespVO;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookSignature;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookSignatureHeaders;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookVerifyResult;
import cn.iocoder.yudao.module.agent.service.notice.AgentNoticeCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 卡片验收/打回回调 Controller
 *
 * <p>回调是公开 Webhook，先校验 HMAC 签名与回放防护（TASK-NOTICE-03），再交给
 * 回调服务复用控制面动作。永久失败返回 4xx，瞬时失败返回 5xx 供卡片平台重试。</p>
 */
@Tag(name = "管理后台 - 通知回调")
@RestController
@RequestMapping("/agent/notice")
public class AgentNoticeCallbackController {

    @Resource
    private WebhookSignature webhookSignature;

    @Resource
    private AgentNoticeCallbackService callbackService;

    @PostMapping("/callback")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "卡片验收/打回回调")
    public ResponseEntity<AgentNoticeCallbackRespVO> callback(
            @RequestHeader(value = WebhookSignatureHeaders.TIMESTAMP, required = false) String timestamp,
            @RequestHeader(value = WebhookSignatureHeaders.NONCE, required = false) String nonce,
            @RequestHeader(value = WebhookSignatureHeaders.SIGNATURE, required = false) String signature,
            @RequestBody String rawBody) {
        WebhookVerifyResult verify = webhookSignature.verify(timestamp, nonce, signature, rawBody);
        if (!verify.isValid()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(rejected(false, "回调签名校验失败：" + verify.getDescription()));
        }

        AgentNoticeCallbackReqVO request = parse(rawBody);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(rejected(false, "回调请求体不是合法的 JSON"));
        }

        AgentNoticeCallbackRespVO result = callbackService.handle(request);
        HttpStatus status = result.isSuccess() ? HttpStatus.OK
                : (result.isRetryable() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(status).body(result);
    }

    private static AgentNoticeCallbackReqVO parse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        return JsonUtils.parseObjectQuietly(rawBody, AgentNoticeCallbackReqVO.class);
    }

    private static AgentNoticeCallbackRespVO rejected(boolean retryable, String message) {
        AgentNoticeCallbackRespVO response = new AgentNoticeCallbackRespVO();
        response.setSuccess(false);
        response.setRetryable(retryable);
        response.setMessage(message);
        return response;
    }

}
