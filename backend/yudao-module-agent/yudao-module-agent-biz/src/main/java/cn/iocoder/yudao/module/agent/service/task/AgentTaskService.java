package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskOperationRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskPageReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskSubmitRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskUpdateDocumentReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskUpdateDocumentRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;

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
     * 获得任务详情。
     *
     * @param id 任务 ID
     * @return 任务信息，不存在时返回 null
     */
    AgentTaskDO getTask(Long id);

    /**
     * 获得任务分页列表，支持按任务编号、标题、状态、优先级等过滤。
     *
     * @param pageReqVO 分页条件
     * @return 任务分页列表
     */
    PageResult<AgentTaskDO> getTaskPage(AgentTaskPageReqVO pageReqVO);

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
     * 将已取消、已打回或执行失败的任务克隆为全新任务编号。
     *
     * <p>克隆时复制任务文档与项目引用、生成新 {@code task_no}、清空执行结果与耗时、
     * 执行代次归零，原任务保持不变；审计动作与重投（{@link #reEnqueue}）区分。</p>
     *
     * @param id             原任务 ID
     * @param idempotencyKey 请求幂等键（请求头 X-Idempotency-Key）
     * @return 克隆后的任务编号、状态与文档版本
     */
    AgentTaskOperationRespVO reEnqueueClone(Long id, String idempotencyKey);

    /**
     * 软删除已取消的任务。
     *
     * @param id             任务 ID
     * @param idempotencyKey 请求幂等键（请求头 X-Idempotency-Key）
     * @return 操作后的任务编号、状态与文档版本
     */
    AgentTaskOperationRespVO deleteTask(Long id, String idempotencyKey);

    /**
     * 验收通过：WAITING_ACCEPTANCE -> ACCEPTED。
     *
     * @param id             任务 ID
     * @param idempotencyKey 请求幂等键（请求头 X-Idempotency-Key）
     * @return 操作后的任务状态与操作记录
     */
    AgentTaskOperationRespVO accept(Long id, String idempotencyKey);

    /**
     * 打回：WAITING_ACCEPTANCE -> REJECTED，并记录打回反馈。
     *
     * @param id             任务 ID
     * @param feedback       打回反馈（必填）
     * @param idempotencyKey 请求幂等键（请求头 X-Idempotency-Key）
     * @return 操作后的任务状态与操作记录
     */
    AgentTaskOperationRespVO reject(Long id, String feedback, String idempotencyKey);

    /**
     * 合并冲突人工处理入口：ACCEPTED -> MERGE_CONFLICT_PENDING_MANUAL。
     *
     * @param id             任务 ID
     * @param idempotencyKey 请求幂等键（请求头 X-Idempotency-Key）
     * @return 操作后的任务状态与操作记录
     */
    AgentTaskOperationRespVO markMergeConflict(Long id, String idempotencyKey);

}
