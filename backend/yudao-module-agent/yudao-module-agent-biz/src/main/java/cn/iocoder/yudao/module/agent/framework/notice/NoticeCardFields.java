package cn.iocoder.yudao.module.agent.framework.notice;

import java.util.Set;

/**
 * 通知卡片字段名契约
 *
 * <p>集中登记三类卡片（待验收、执行失败、合并冲突）的稳定字段名。
 * 卡片装配和 {@code NoticeContractTest} 都依赖这里，禁止随意增删字段。</p>
 *
 * @author TaskForge
 */
public final class NoticeCardFields {

    /** 待验收卡片字段 */
    public static final String BRANCHES = "branches";
    public static final String CHANGED_FILES = "changedFiles";
    public static final String DIFF_STAT = "diffStat";
    public static final String TEST_SUMMARY = "testSummary";
    public static final String RETRY_TIMES = "retryTimes";
    public static final String COST_MS = "costMs";

    /** 执行失败卡片字段 */
    public static final String FAILED_AT = "failedAt";
    public static final String ERROR_SUMMARY = "errorSummary";
    public static final String LOG_TAIL = "logTail";

    /** 合并冲突卡片字段 */
    public static final String CONFLICTS = "conflicts";

    /** 待验收卡片完整字段集合 */
    public static final Set<String> WAITING_ACCEPTANCE = Set.of(
            BRANCHES, CHANGED_FILES, DIFF_STAT, TEST_SUMMARY, RETRY_TIMES, COST_MS);

    /** 执行失败卡片完整字段集合 */
    public static final Set<String> FAILED = Set.of(
            FAILED_AT, ERROR_SUMMARY, LOG_TAIL, RETRY_TIMES, COST_MS);

    /** 合并冲突卡片完整字段集合 */
    public static final Set<String> MERGE_CONFLICT = Set.of(CONFLICTS);

    private NoticeCardFields() {
    }

}
