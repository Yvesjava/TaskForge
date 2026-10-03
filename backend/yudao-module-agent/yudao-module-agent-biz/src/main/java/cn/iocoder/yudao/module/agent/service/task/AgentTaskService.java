package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskOperationRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskSubmitRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskUpdateDocumentReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskUpdateDocumentRespVO;

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

    /**
     * 编辑暂停任务的文档与执行参数。
     *
     * @param id             任务 ID
     * @param reqVO          编辑请求（携带当前 docVersion）
     * @param idempotencyKey 请求幂等键（请求头 X-Idempotency-Key）
     * @param ifMatch        乐观锁请求头 If-Match（需与 reqVO.docVersion 一致）
     * @return 更新后的任务编号、状态与文档版本
     */
    AgentTaskUpdateDocumentRespVO updateDocument(Long id,
                                                 AgentTaskUpdateDocumentReqVO reqVO,
                                                 String idempotencyKey,
                                                 String ifMatch);

    /**
     * 暂停排队中的任务。
     *
     * @param id             任务 ID
     * @param idempotencyKey 请求幂等键（请求头 X-Idempotency-Key）
     * @return 操作后的任务编号、状态与文档版本
     */
    AgentTaskOperationRespVO pause(Long id, String idempotencyKey);

    /**
     * 恢复暂停任务到待调度队列。
     *
     * @param id             任务 ID
     * @param idempotencyKey 请求幂等键（请求头 X-Idempotency-Key）
     * @return 操作后的任务编号、状态与文档版本
     */
    AgentTaskOperationRespVO resume(Long id, String idempotencyKey);

    /**
     * 取消排队中或已暂停的任务。
     *
     * @param id             任务 ID
     * @param cancelReason   取消原因
     * @param idempotencyKey 请求幂等键（请求头 X-Idempotency-Key）
     * @return 操作后的任务编号、状态与文档版本
     */
    AgentTaskOperationRespVO cancel(Long id, String cancelReason, String idempotencyKey);

    /**
     * 将已取消、已打回或执行失败的任务重新入队。
     *
     * @param id             任务 ID
     * @param idempotencyKey 请求幂等键（请求头 X-Idempotency-Key）
     * @return 操作后的任务编号、状态与文档版本
     */
    AgentTaskOperationRespVO reEnqueue(Long id, String idempotencyKey);

    /**
     * 软删除已取消的任务。
     *
     * @param id             任务 ID
     * @param idempotencyKey 请求幂等键（请求头 X-Idempotency-Key）
     * @return 操作后的任务编号、状态与文档版本
     */
    AgentTaskOperationRespVO deleteTask(Long id, String idempotencyKey);

}
