package cn.iocoder.yudao.module.agent.service.ops;

import cn.iocoder.yudao.module.agent.dal.dataobject.AgentNoticeOutboxDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentNoticeOutboxMapper;
import cn.iocoder.yudao.module.agent.framework.ops.AgentCompensationProperties;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookAttempt;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookRequest;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookResponse;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookSendResult;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知投递 Outbox 服务单元测试
 *
 * <p>覆盖失败通知持久化、到期重发、成功置 SENT、失败推进重试与终态判定。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentNoticeOutboxTest {

    @Mock
    private AgentNoticeOutboxMapper outboxMapper;

    @Mock
    private WebhookSender webhookSender;

    @Mock
    private AgentCompensationProperties properties;

    @InjectMocks
    private AgentNoticeOutboxServiceImpl outboxService;

    @Test
    void record_persistsPendingOutboxWithRenderedPayload() {
        outboxService.record("TASK-1", "FAILED", "WECOM", "https://example.com/hook",
                Map.of("msgtype", "text"), 3);

        ArgumentCaptor<AgentNoticeOutboxDO> captor =
                ArgumentCaptor.forClass(AgentNoticeOutboxDO.class);
        verify(outboxMapper).insert(captor.capture());
        AgentNoticeOutboxDO record = captor.getValue();
        assertThat(record.getStatus()).isEqualTo("PENDING");
        assertThat(record.getRetryCount()).isZero();
        assertThat(record.getMaxRetries()).isEqualTo(3);
        assertThat(record.getPayload()).contains("msgtype");
        assertThat(record.getNextRetryTime()).isNotNull();
    }

    @Test
    void dispatchDue_marksSentWhenWebhookSucceeds() {
        AgentNoticeOutboxDO record = outboxRecord(7L, 0, 3);
        when(outboxMapper.selectDueOutbox(anyInt(), any(LocalDateTime.class)))
                .thenReturn(List.of(record));
        when(webhookSender.send(any(WebhookRequest.class)))
                .thenReturn(successResult());

        AgentNoticeDispatchResult result = outboxService.dispatchDue(50);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(outboxMapper).updateOutboxResult(eq(7L), eq("SENT"), eq(1),
                isNull(), eq(""));
    }

    @Test
    void dispatchDue_keepsRetryingWhenBelowMaxRetries() {
        AgentNoticeOutboxDO record = outboxRecord(8L, 1, 3);
        when(outboxMapper.selectDueOutbox(anyInt(), any(LocalDateTime.class)))
                .thenReturn(List.of(record));
        when(properties.getNoticeRetryBackoffSeconds()).thenReturn(60L);
        when(webhookSender.send(any(WebhookRequest.class))).thenReturn(failureResult());

        AgentNoticeDispatchResult result = outboxService.dispatchDue(50);

        assertThat(result.sent()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        verify(outboxMapper).updateOutboxResult(eq(8L), eq("FAILED"), eq(2),
                any(LocalDateTime.class), any(String.class));
    }

    @Test
    void dispatchDue_marksTerminalFailedWhenMaxRetriesReached() {
        AgentNoticeOutboxDO record = outboxRecord(9L, 2, 3);
        when(outboxMapper.selectDueOutbox(anyInt(), any(LocalDateTime.class)))
                .thenReturn(List.of(record));
        when(webhookSender.send(any(WebhookRequest.class))).thenReturn(failureResult());

        AgentNoticeDispatchResult result = outboxService.dispatchDue(50);

        assertThat(result.failed()).isEqualTo(1);
        verify(outboxMapper).updateOutboxResult(eq(9L), eq("FAILED"), eq(3),
                isNull(), any(String.class));
    }

    private static AgentNoticeOutboxDO outboxRecord(Long id, int retryCount, int maxRetries) {
        return AgentNoticeOutboxDO.builder()
                .id(id)
                .taskNo("TASK-1")
                .event("FAILED")
                .platform("WECOM")
                .webhookUrl("https://example.com/hook")
                .payload("{\"msgtype\":\"text\"}")
                .status("FAILED")
                .retryCount(retryCount)
                .maxRetries(maxRetries)
                .nextRetryTime(LocalDateTime.now().minusSeconds(1))
                .build();
    }

    private static WebhookSendResult successResult() {
        return new WebhookSendResult(List.of(
                new WebhookAttempt(1, WebhookResponse.success(200, "ok"), 1L)), 3);
    }

    private static WebhookSendResult failureResult() {
        return new WebhookSendResult(List.of(
                new WebhookAttempt(1, WebhookResponse.failure(0, "", "connection refused"), 1L)), 3);
    }

}
