package cn.iocoder.yudao.module.agent.framework.observability;

import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TaskForge 可观测性组件。
 *
 * <p>统一承载两类能力：</p>
 * <ul>
 *     <li>结构化日志上下文：在任务执行与外部调用期间，把 {@code taskNo}、
 *     {@code workerId}、{@code generation}、{@code traceId}、{@code phase}
 *     写入 SLF4J MDC，供日志布局输出结构化字段。</li>
 *     <li>耗时与失败指标：通过 Micrometer 记录任务执行与外部调用的耗时
 *     Timer 与失败 Counter，维度覆盖任务、Worker 与外部调用。</li>
 * </ul>
 *
 * <p>生产环境优先注入 Spring 容器中的 {@link MeterRegistry}；当容器未提供
 * （例如未启用 actuator）时回退到 Micrometer 全局注册表。单元测试可直接使用
 * {@link #AgentObservability(MeterRegistry)} 注入 {@code SimpleMeterRegistry}。</p>
 *
 * @author TaskForge
 */
@Component
public class AgentObservability {

    /** MDC 字段名：任务编号。 */
    public static final String MDC_TASK_NO = "taskNo";

    /** MDC 字段名：Worker 身份。 */
    public static final String MDC_WORKER_ID = "workerId";

    /** MDC 字段名：执行代次。 */
    public static final String MDC_GENERATION = "generation";

    /** MDC 字段名：链路追踪编号。 */
    public static final String MDC_TRACE_ID = "traceId";

    /** MDC 字段名：执行阶段。 */
    public static final String MDC_PHASE = "phase";

    /** 任务执行耗时指标名。 */
    public static final String METRIC_TASK_DURATION = "taskforge.task.duration";

    /** 任务执行失败指标名。 */
    public static final String METRIC_TASK_FAILURES = "taskforge.task.failures";

    /** 外部调用耗时指标名。 */
    public static final String METRIC_EXTERNAL_CALL_DURATION = "taskforge.external_call.duration";

    /** 外部调用失败指标名。 */
    public static final String METRIC_EXTERNAL_CALL_FAILURES = "taskforge.external_call.failures";

    /** 指标标签：任务编号。 */
    public static final String TAG_TASK_NO = "task_no";

    /** 指标标签：Worker 身份。 */
    public static final String TAG_WORKER_ID = "worker_id";

    /** 指标标签：执行结果。 */
    public static final String TAG_OUTCOME = "outcome";

    /** 指标标签：外部调用类型。 */
    public static final String TAG_KIND = "kind";

    /** 指标标签：外部调用目标（脱敏后的主机名）。 */
    public static final String TAG_TARGET = "target";

    /** 指标标签：失败类型。 */
    public static final String TAG_FAILURE_TYPE = "failure_type";

    private final MeterRegistry registry;

    /**
     * Spring 默认构造：优先取容器中的 {@link MeterRegistry}，缺失时回退到全局注册表。
     */
    @Autowired
    public AgentObservability(ObjectProvider<MeterRegistry> registryProvider) {
        this(registryProvider.getIfAvailable(() -> Metrics.globalRegistry));
    }

    /**
     * 供单元测试与手工装配使用的构造器。
     */
    public AgentObservability(MeterRegistry registry) {
        this.registry = registry == null ? Metrics.globalRegistry : registry;
    }

    /**
     * 开始一次任务执行观测。
     *
     * @param taskNo     任务编号
     * @param workerId   Worker 身份
     * @param generation 执行代次
     * @param phase      执行阶段
     * @return 任务观测作用域，调用方须在 finally 中关闭
     */
    public TaskObservation startTask(String taskNo, String workerId, Long generation, String phase) {
        return new TaskObservation(taskNo, workerId, generation, phase);
    }

    /**
     * 开始一次外部调用观测。
     *
     * @param kind   外部调用类型，例如 git_api、webhook
     * @param target 脱敏后的目标主机名
     * @return 外部调用观测作用域，调用方须在 finally 中关闭
     */
    public ExternalCallObservation startExternalCall(String kind, String target) {
        return new ExternalCallObservation(kind, target);
    }

    /**
     * 当前线程的 OpenTelemetry TraceId；无有效链路时为空字符串。
     */
    public String traceId() {
        return TracerUtils.getTraceId();
    }

    private static Tag tag(String key, String value) {
        return Tag.of(key, value == null ? "" : value);
    }

    /**
     * 任务执行观测作用域。
     */
    public final class TaskObservation implements AutoCloseable {

        private final Timer.Sample sample;
        private final String taskNo;
        private final String workerId;
        private final Map<String, String> previousMdc;
        private String outcome = "unknown";

        private TaskObservation(String taskNo, String workerId, Long generation, String phase) {
            this.taskNo = taskNo;
            this.workerId = workerId;
            this.previousMdc = capture(MDC_TASK_NO, MDC_WORKER_ID, MDC_GENERATION, MDC_TRACE_ID, MDC_PHASE);
            put(MDC_TASK_NO, taskNo);
            put(MDC_WORKER_ID, workerId);
            put(MDC_GENERATION, generation == null ? null : String.valueOf(generation));
            put(MDC_TRACE_ID, TracerUtils.getTraceId());
            put(MDC_PHASE, phase);
            this.sample = Timer.start(registry);
        }

        public void success() {
            this.outcome = "success";
        }

        public void timeout() {
            this.outcome = "timeout";
        }

        public void error() {
            this.outcome = "error";
        }

        public void outcome(String outcome) {
            this.outcome = outcome == null || outcome.isBlank() ? "unknown" : outcome;
        }

        @Override
        public void close() {
            try {
                sample.stop(registry.timer(METRIC_TASK_DURATION, List.of(
                        tag(TAG_TASK_NO, taskNo),
                        tag(TAG_WORKER_ID, workerId),
                        tag(TAG_OUTCOME, outcome))));
                if (!"success".equals(outcome)) {
                    registry.counter(METRIC_TASK_FAILURES, List.of(
                            tag(TAG_TASK_NO, taskNo),
                            tag(TAG_WORKER_ID, workerId),
                            tag(TAG_FAILURE_TYPE, outcome))).increment();
                }
            } finally {
                restore(previousMdc);
            }
        }
    }

    /**
     * 外部调用观测作用域。
     */
    public final class ExternalCallObservation implements AutoCloseable {

        private final Timer.Sample sample;
        private final String kind;
        private final String target;
        private final Map<String, String> previousMdc;
        private String outcome = "unknown";

        private ExternalCallObservation(String kind, String target) {
            this.kind = kind;
            this.target = target;
            this.previousMdc = capture(MDC_TRACE_ID);
            put(MDC_TRACE_ID, TracerUtils.getTraceId());
            this.sample = Timer.start(registry);
        }

        public void success() {
            this.outcome = "success";
        }

        public void failure() {
            this.outcome = "failure";
        }

        public void timeout() {
            this.outcome = "timeout";
        }

        @Override
        public void close() {
            try {
                sample.stop(registry.timer(METRIC_EXTERNAL_CALL_DURATION, List.of(
                        tag(TAG_KIND, kind),
                        tag(TAG_TARGET, target),
                        tag(TAG_OUTCOME, outcome))));
                if (!"success".equals(outcome)) {
                    registry.counter(METRIC_EXTERNAL_CALL_FAILURES, List.of(
                            tag(TAG_KIND, kind),
                            tag(TAG_TARGET, target),
                            tag(TAG_FAILURE_TYPE, outcome))).increment();
                }
            } finally {
                restore(previousMdc);
            }
        }
    }

    private static Map<String, String> capture(String... keys) {
        Map<String, String> previous = new LinkedHashMap<>();
        for (String key : keys) {
            previous.put(key, MDC.get(key));
        }
        return previous;
    }

    private static void put(String key, String value) {
        if (value == null || value.isEmpty()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    private static void restore(Map<String, String> previous) {
        previous.forEach((key, value) -> {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        });
    }

}
