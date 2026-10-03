package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 任务状态合法转换矩阵
 *
 * <p>固化 {@code docs/技术开发与架构设计文档.md} §3.9 中允许的最小转换集合。
 * 未在矩阵中登记的（状态、动作、目标状态）组合一律返回非法。</p>
 *
 * @author TaskForge
 */
public final class AgentTaskTransitionMatrix {

    private static final Map<AgentTaskStatus, Map<AgentTaskAction, Set<AgentTaskStatus>>> MATRIX = buildMatrix();

    private AgentTaskTransitionMatrix() {
    }

    public static boolean isLegal(AgentTaskStatus from, AgentTaskAction action, AgentTaskStatus to) {
        return from != null && action != null && to != null && legalTargets(from, action).contains(to);
    }

    public static Set<AgentTaskStatus> legalTargets(AgentTaskStatus from, AgentTaskAction action) {
        if (from == null || action == null) {
            return Collections.emptySet();
        }
        Map<AgentTaskAction, Set<AgentTaskStatus>> byAction = MATRIX.get(from);
        if (byAction == null) {
            return Collections.emptySet();
        }
        return byAction.getOrDefault(action, Collections.emptySet());
    }

    private static Map<AgentTaskStatus, Map<AgentTaskAction, Set<AgentTaskStatus>>> buildMatrix() {
        Map<AgentTaskStatus, Map<AgentTaskAction, Set<AgentTaskStatus>>> matrix = new EnumMap<>(AgentTaskStatus.class);

        register(matrix, AgentTaskStatus.PENDING, AgentTaskAction.PAUSE, AgentTaskStatus.PAUSED);
        register(matrix, AgentTaskStatus.PENDING, AgentTaskAction.CLAIM, AgentTaskStatus.RUNNING);
        register(matrix, AgentTaskStatus.PENDING, AgentTaskAction.CANCEL, AgentTaskStatus.CANCELED);

        register(matrix, AgentTaskStatus.PAUSED, AgentTaskAction.EDIT, AgentTaskStatus.PAUSED);
        register(matrix, AgentTaskStatus.PAUSED, AgentTaskAction.RESUME, AgentTaskStatus.PENDING);
        register(matrix, AgentTaskStatus.PAUSED, AgentTaskAction.RESET, AgentTaskStatus.RESETTING);
        register(matrix, AgentTaskStatus.PAUSED, AgentTaskAction.CANCEL, AgentTaskStatus.CANCELED);

        register(matrix, AgentTaskStatus.RUNNING, AgentTaskAction.SELF_VERIFY_PASS, AgentTaskStatus.WAITING_ACCEPTANCE);
        register(matrix, AgentTaskStatus.RUNNING, AgentTaskAction.TIMEOUT, AgentTaskStatus.FAILED);
        register(matrix, AgentTaskStatus.RUNNING, AgentTaskAction.ERROR, AgentTaskStatus.FAILED);
        register(matrix, AgentTaskStatus.RUNNING, AgentTaskAction.HEARTBEAT_EXPIRED, AgentTaskStatus.FAILED);

        register(matrix, AgentTaskStatus.WAITING_ACCEPTANCE, AgentTaskAction.ACCEPT, AgentTaskStatus.ACCEPTED);
        register(matrix, AgentTaskStatus.WAITING_ACCEPTANCE, AgentTaskAction.REJECT, AgentTaskStatus.REJECTED);

        register(matrix, AgentTaskStatus.ACCEPTED, AgentTaskAction.MERGE_PASS, AgentTaskStatus.COMPLETED);
        register(matrix, AgentTaskStatus.ACCEPTED, AgentTaskAction.MERGE_CONFLICT,
                AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL);
        register(matrix, AgentTaskStatus.MERGE_CONFLICT_PENDING_MANUAL, AgentTaskAction.MERGE_RETRY,
                AgentTaskStatus.ACCEPTED);

        register(matrix, AgentTaskStatus.REJECTED, AgentTaskAction.RE_ENQUEUE, AgentTaskStatus.PENDING);
        register(matrix, AgentTaskStatus.FAILED, AgentTaskAction.RE_ENQUEUE, AgentTaskStatus.PENDING);
        register(matrix, AgentTaskStatus.CANCELED, AgentTaskAction.RE_ENQUEUE, AgentTaskStatus.PENDING);
        register(matrix, AgentTaskStatus.CANCELED, AgentTaskAction.DELETE, AgentTaskStatus.DELETED);

        register(matrix, AgentTaskStatus.RESETTING, AgentTaskAction.CLEANUP_PASS, AgentTaskStatus.PENDING);
        register(matrix, AgentTaskStatus.RESETTING, AgentTaskAction.CLEANUP_PASS, AgentTaskStatus.PAUSED);

        return Collections.unmodifiableMap(matrix);
    }

    private static void register(Map<AgentTaskStatus, Map<AgentTaskAction, Set<AgentTaskStatus>>> matrix,
                                 AgentTaskStatus from, AgentTaskAction action, AgentTaskStatus to) {
        matrix.computeIfAbsent(from, key -> new EnumMap<>(AgentTaskAction.class))
                .computeIfAbsent(action, key -> EnumSet.noneOf(AgentTaskStatus.class))
                .add(to);
    }

}
