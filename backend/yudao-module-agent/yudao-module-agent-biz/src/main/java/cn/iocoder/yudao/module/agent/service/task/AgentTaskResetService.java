package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskResetRespVO;

/**
 * 任务重置服务
 *
 * @author TaskForge
 */
public interface AgentTaskResetService {

    /**
     * 重置任务：{@code PAUSED -> RESETTING -> PENDING/PAUSED}。
     *
     * @param taskId     任务 ID
     * @param keepPaused true 表示重置后保持 {@code PAUSED}，否则回到 {@code PENDING}
     * @param idempotencyKey 请求幂等键（请求头 X-Idempotency-Key），重复请求返回当前结果
     * @return 重置恢复结果（最终状态）
     */
    AgentTaskResetRespVO reset(Long taskId, boolean keepPaused, String idempotencyKey);

}
