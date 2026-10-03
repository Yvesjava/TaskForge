package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskSubmitRespVO;

/**
 * 任务控制面 Service
 *
 * @author TaskForge
 */
public interface AgentTaskService {

    /**
     * 投递任务文档，生成任务编号并保存初始文档版本。
     *
     * @param document      完整任务 Markdown 文档
     * @param idempotencyKey 请求幂等键（请求头 X-Idempotency-Key）
     * @return 任务编号、状态与文档版本
     */
    AgentTaskSubmitRespVO submit(String document, String idempotencyKey);

}
