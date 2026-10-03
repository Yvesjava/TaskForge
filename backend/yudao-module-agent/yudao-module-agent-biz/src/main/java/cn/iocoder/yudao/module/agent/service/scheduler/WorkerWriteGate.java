package cn.iocoder.yudao.module.agent.service.scheduler;

import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Worker 停止写入门禁
 *
 * <p>在 Worker 提交、推送或写回结果前调用，统一判定当前 Worker 是否仍具备写入资格。
 * 判定同时检查取消信号与 Redis 租约持有者：任务被取消或已失租时返回全禁止，Worker
 * 必须停止写入并进入清理流程。</p>
 *
 * @author TaskForge
 */
@Component
public class WorkerWriteGate {

    private final AgentTaskCancelSignalService cancelSignalService;

    private final AgentTaskLeaseService leaseService;

    public WorkerWriteGate(AgentTaskCancelSignalService cancelSignalService,
                           AgentTaskLeaseService leaseService) {
        this.cancelSignalService = Objects.requireNonNull(cancelSignalService, "cancelSignalService 不能为空");
        this.leaseService = Objects.requireNonNull(leaseService, "leaseService 不能为空");
    }

    /**
     * 判定当前 Worker 是否仍可提交、推送与写回结果。
     *
     * @param taskId     任务主键 ID
     * @param workerId   当前 Worker 身份
     * @param generation 当前执行代次
     * @return 三项写入动作的允许状态
     */
    public WorkerWriteDecision check(Long taskId, String workerId, Long generation) {
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(workerId, "workerId 不能为空");
        Objects.requireNonNull(generation, "generation 不能为空");

        boolean allowed = !cancelSignalService.isCanceled(taskId, generation)
                && leaseService.isCurrentHolder(taskId, workerId, generation);
        return new WorkerWriteDecision(allowed, allowed, allowed);
    }

}
