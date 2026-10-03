package cn.iocoder.yudao.module.agent.framework.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-OPS-03 可观测性契约测试。
 *
 * <p>覆盖结构化日志上下文（MDC）与 Micrometer 耗时/失败指标，验证一次执行可
 * 按任务、Worker 与外部调用三个维度定位。</p>
 */
class AgentObservabilityTest {

    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";

    private static final String SPAN_ID = "0123456789abcdef";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final AgentObservability observability = new AgentObservability(registry);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void taskObservation_setsStructuredMdcAndClearsOnClose() {
        try (AgentObservability.TaskObservation observation =
                     observability.startTask("TASK-1", "worker-1", 7L, "execute")) {
            assertThat(MDC.get(AgentObservability.MDC_TASK_NO)).isEqualTo("TASK-1");
            assertThat(MDC.get(AgentObservability.MDC_WORKER_ID)).isEqualTo("worker-1");
            assertThat(MDC.get(AgentObservability.MDC_GENERATION)).isEqualTo("7");
            assertThat(MDC.get(AgentObservability.MDC_PHASE)).isEqualTo("execute");
        }

        assertThat(MDC.get(AgentObservability.MDC_TASK_NO)).isNull();
        assertThat(MDC.get(AgentObservability.MDC_WORKER_ID)).isNull();
        assertThat(MDC.get(AgentObservability.MDC_GENERATION)).isNull();
        assertThat(MDC.get(AgentObservability.MDC_PHASE)).isNull();
    }

    @Test
    void taskObservation_recordsDurationAndFailureMetrics() {
        try (AgentObservability.TaskObservation observation =
                     observability.startTask("TASK-1", "worker-1", 7L, "execute")) {
            observation.error();
        }

        Timer timer = registry.find(AgentObservability.METRIC_TASK_DURATION)
                .tag(AgentObservability.TAG_TASK_NO, "TASK-1")
                .tag(AgentObservability.TAG_WORKER_ID, "worker-1")
                .tag(AgentObservability.TAG_OUTCOME, "error")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        Counter failures = registry.find(AgentObservability.METRIC_TASK_FAILURES)
                .tag(AgentObservability.TAG_TASK_NO, "TASK-1")
                .tag(AgentObservability.TAG_WORKER_ID, "worker-1")
                .tag(AgentObservability.TAG_FAILURE_TYPE, "error")
                .counter();
        assertThat(failures).isNotNull();
        assertThat(failures.count()).isEqualTo(1);
    }

    @Test
    void taskObservation_successDoesNotRecordFailure() {
        try (AgentObservability.TaskObservation observation =
                     observability.startTask("TASK-1", "worker-1", 7L, "execute")) {
            observation.success();
        }

        Timer timer = registry.find(AgentObservability.METRIC_TASK_DURATION)
                .tag(AgentObservability.TAG_OUTCOME, "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        assertThat(registry.find(AgentObservability.METRIC_TASK_FAILURES)
                .tag(AgentObservability.TAG_TASK_NO, "TASK-1")
                .counter()).isNull();
    }

    @Test
    void externalCallObservation_recordsDurationAndFailureMetrics() {
        try (AgentObservability.ExternalCallObservation observation =
                     observability.startExternalCall("git_api", "github.com")) {
            observation.failure();
        }

        Timer timer = registry.find(AgentObservability.METRIC_EXTERNAL_CALL_DURATION)
                .tag(AgentObservability.TAG_KIND, "git_api")
                .tag(AgentObservability.TAG_TARGET, "github.com")
                .tag(AgentObservability.TAG_OUTCOME, "failure")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        Counter failures = registry.find(AgentObservability.METRIC_EXTERNAL_CALL_FAILURES)
                .tag(AgentObservability.TAG_KIND, "git_api")
                .tag(AgentObservability.TAG_TARGET, "github.com")
                .tag(AgentObservability.TAG_FAILURE_TYPE, "failure")
                .counter();
        assertThat(failures).isNotNull();
        assertThat(failures.count()).isEqualTo(1);
    }

    @Test
    void traceId_isCapturedIntoMdcFromCurrentSpan() {
        SpanContext spanContext = SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
        Span span = Span.wrap(spanContext);
        try (Scope ignored = span.makeCurrent()) {
            assertThat(observability.traceId()).isEqualTo(TRACE_ID);
            try (AgentObservability.TaskObservation observation =
                         observability.startTask("TASK-1", "worker-1", 7L, "execute")) {
                assertThat(MDC.get(AgentObservability.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
            }
        }
    }

    @Test
    void mdcPreviousValuesAreRestoredOnClose() {
        MDC.put(AgentObservability.MDC_TASK_NO, "outer-task");
        try (AgentObservability.TaskObservation observation =
                     observability.startTask("TASK-1", "worker-1", 7L, "execute")) {
            assertThat(MDC.get(AgentObservability.MDC_TASK_NO)).isEqualTo("TASK-1");
        }
        assertThat(MDC.get(AgentObservability.MDC_TASK_NO)).isEqualTo("outer-task");
    }

}
