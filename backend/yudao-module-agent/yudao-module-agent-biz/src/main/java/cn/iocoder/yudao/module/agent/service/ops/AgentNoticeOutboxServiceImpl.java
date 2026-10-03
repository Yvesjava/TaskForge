package cn.iocoder.yudao.module.agent.service.ops;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentNoticeOutboxDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentNoticeOutboxMapper;
import cn.iocoder.yudao.module.agent.framework.notice.NoticeSecretRedactor;
import cn.iocoder.yudao.module.agent.framework.ops.AgentCompensationProperties;
import cn.iocoder.yudao.module.agent.framework.secret.SecretRedactor;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookRequest;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookSendResult;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookSender;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 通知投递 Outbox 服务实现
 *
 * @author TaskForge
 */
@Service
@Slf4j
public class AgentNoticeOutboxServiceImpl implements AgentNoticeOutboxService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_ERROR_LENGTH = 500;

    @Resource
    private AgentNoticeOutboxMapper outboxMapper;

    @Resource
    private WebhookSender webhookSender;

    @Resource
    private AgentCompensationProperties properties;

    @Override
    public void record(String taskNo, String event, String platform, String webhookUrl,
                       Map<String, Object> payload, int maxRetries) {
        AgentNoticeOutboxDO record = AgentNoticeOutboxDO.builder()
                .taskNo(taskNo)
                .event(event)
                .platform(platform)
                .webhookUrl(webhookUrl)
                .payload(JsonUtils.toJsonString(payload == null ? Map.of() : payload))
                .status(STATUS_PENDING)
                .retryCount(0)
                .maxRetries(Math.max(0, maxRetries))
                .nextRetryTime(LocalDateTime.now())
                .build();
        outboxMapper.insert(record);
    }

    @Override
    public AgentNoticeDispatchResult dispatchDue(int batchSize) {
        int limit = batchSize > 0 ? batchSize : properties.getNoticeDispatchBatchSize();
        List<AgentNoticeOutboxDO> dueRecords = outboxMapper.selectDueOutbox(limit, LocalDateTime.now());

        int sent = 0;
        int failed = 0;
        for (AgentNoticeOutboxDO record : dueRecords) {
            if (dispatchOne(record)) {
                sent++;
            } else {
                failed++;
            }
        }
        return new AgentNoticeDispatchResult(dueRecords.size(), sent, failed);
    }

    private boolean dispatchOne(AgentNoticeOutboxDO record) {
        int previousRetries = record.getRetryCount() == null ? 0 : record.getRetryCount();
        int nextRetryCount = previousRetries + 1;
        int maxRetries = record.getMaxRetries() == null
                ? properties.getNoticeMaxRetries() : record.getMaxRetries();

        try {
            Map<String, Object> payload = JsonUtils.parseMap(record.getPayload());
            WebhookRequest request = WebhookRequest.of(record.getWebhookUrl(),
                    payload == null ? Map.of() : payload, null);
            WebhookSendResult result = webhookSender.send(request);
            if (result.isSuccess()) {
                outboxMapper.updateOutboxResult(record.getId(), STATUS_SENT, nextRetryCount, null, "");
                log.info("[NoticeOutbox] 失败通知补偿成功 outboxId={}, taskNo={}, retries={}",
                        record.getId(), record.getTaskNo(), nextRetryCount);
                return true;
            }
            return markFailed(record, nextRetryCount, maxRetries, lastErrorOf(result));
        } catch (Exception e) {
            return markFailed(record, nextRetryCount, maxRetries, SecretRedactor.describe(e, List.of()));
        }
    }

    private boolean markFailed(AgentNoticeOutboxDO record, int nextRetryCount, int maxRetries, String reason) {
        boolean terminal = nextRetryCount >= maxRetries;
        LocalDateTime nextRetryTime = terminal
                ? null : LocalDateTime.now().plusSeconds(properties.getNoticeRetryBackoffSeconds());
        String lastError = truncate(NoticeSecretRedactor.redact(reason));
        outboxMapper.updateOutboxResult(record.getId(), STATUS_FAILED, nextRetryCount,
                nextRetryTime, lastError);
        log.warn("[NoticeOutbox] 失败通知补偿仍失败 outboxId={}, taskNo={}, retries={}, terminal={}, reason={}",
                record.getId(), record.getTaskNo(), nextRetryCount, terminal, lastError);
        return false;
    }

    private String lastErrorOf(WebhookSendResult result) {
        if (result == null || result.lastAttempt() == null) {
            return "未知错误";
        }
        String reason = result.lastAttempt().failureReason();
        return reason == null || reason.isBlank() ? "未知错误" : reason;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }

}
