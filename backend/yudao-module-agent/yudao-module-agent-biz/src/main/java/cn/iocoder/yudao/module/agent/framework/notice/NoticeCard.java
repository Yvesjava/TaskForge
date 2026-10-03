package cn.iocoder.yudao.module.agent.framework.notice;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.agent.framework.notice.NoticeCardFields.BRANCHES;
import static cn.iocoder.yudao.module.agent.framework.notice.NoticeCardFields.CHANGED_FILES;
import static cn.iocoder.yudao.module.agent.framework.notice.NoticeCardFields.CONFLICTS;
import static cn.iocoder.yudao.module.agent.framework.notice.NoticeCardFields.COST_MS;
import static cn.iocoder.yudao.module.agent.framework.notice.NoticeCardFields.DIFF_STAT;
import static cn.iocoder.yudao.module.agent.framework.notice.NoticeCardFields.ERROR_SUMMARY;
import static cn.iocoder.yudao.module.agent.framework.notice.NoticeCardFields.FAILED_AT;
import static cn.iocoder.yudao.module.agent.framework.notice.NoticeCardFields.LOG_TAIL;
import static cn.iocoder.yudao.module.agent.framework.notice.NoticeCardFields.RETRY_TIMES;
import static cn.iocoder.yudao.module.agent.framework.notice.NoticeCardFields.TEST_SUMMARY;

/**
 * 通知卡片数据契约
 *
 * <p>卡片通过 {@link #toView()} 输出稳定的线格式，供企业微信/飞书适配器翻译。
 * 装配时会先经过 {@link NoticeSecretRedactor} 脱敏，确保卡片不含任何秘密。</p>
 *
 * @author TaskForge
 */
@Getter
@EqualsAndHashCode
@ToString
public final class NoticeCard {

    private final String event;
    private final String taskNo;
    private final String title;
    private final String status;
    private final String reportVersion;
    private final Map<String, Object> fields;

    private NoticeCard(NoticeEvent event, String taskNo, String title, String reportVersion,
                       Map<String, Object> fields) {
        this.event = event.getValue();
        this.taskNo = NoticeSecretRedactor.redact(taskNo);
        this.title = NoticeSecretRedactor.redact(title);
        this.status = event.getLabel();
        this.reportVersion = NoticeSecretRedactor.redact(reportVersion);
        this.fields = Collections.unmodifiableMap(sanitizeMap(fields));
    }

    public static NoticeCard waitingAcceptance(String taskNo, String title, String reportVersion,
                                               List<NoticeBranchRef> branches, List<String> changedFiles,
                                               String diffStat, String testSummary, int retryTimes, long costMs) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(BRANCHES, branches == null
                ? List.of() : branches.stream().map(NoticeBranchRef::toView).toList());
        fields.put(CHANGED_FILES, changedFiles == null ? List.of() : List.copyOf(changedFiles));
        fields.put(DIFF_STAT, diffStat);
        fields.put(TEST_SUMMARY, testSummary);
        fields.put(RETRY_TIMES, retryTimes);
        fields.put(COST_MS, costMs);
        return new NoticeCard(NoticeEvent.WAITING_ACCEPTANCE, taskNo, title, reportVersion, fields);
    }

    public static NoticeCard failed(String taskNo, String title, String reportVersion, String failedAt,
                                    String errorSummary, String logTail, int retryTimes, long costMs) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(FAILED_AT, failedAt);
        fields.put(ERROR_SUMMARY, errorSummary);
        fields.put(LOG_TAIL, logTail);
        fields.put(RETRY_TIMES, retryTimes);
        fields.put(COST_MS, costMs);
        return new NoticeCard(NoticeEvent.FAILED, taskNo, title, reportVersion, fields);
    }

    public static NoticeCard mergeConflict(String taskNo, String title, String reportVersion,
                                           List<NoticeConflictRef> conflicts) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(CONFLICTS, conflicts == null
                ? List.of() : conflicts.stream().map(NoticeConflictRef::toView).toList());
        return new NoticeCard(NoticeEvent.MERGE_CONFLICT_PENDING_MANUAL, taskNo, title, reportVersion, fields);
    }

    /**
     * 输出稳定线格式：事件、任务编号、标题、状态、报告版本与卡片字段。
     */
    public Map<String, Object> toView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("event", event);
        view.put("taskNo", taskNo);
        view.put("title", title);
        view.put("status", status);
        view.put("reportVersion", reportVersion);
        view.put("fields", fields);
        return view;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sanitizeMap(Map<String, Object> fields) {
        return (Map<String, Object>) NoticeSecretRedactor.sanitize(fields);
    }

}
