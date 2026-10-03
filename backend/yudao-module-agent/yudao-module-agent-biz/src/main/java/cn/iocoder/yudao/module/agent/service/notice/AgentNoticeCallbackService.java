package cn.iocoder.yudao.module.agent.service.notice;

import cn.iocoder.yudao.module.agent.controller.admin.notice.vo.AgentNoticeCallbackReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.notice.vo.AgentNoticeCallbackRespVO;

/**
 * 卡片验收/打回回调 Service
 *
 * <p>回调只负责鉴权、装配与结果映射，真正的状态推进复用
 * {@link cn.iocoder.yudao.module.agent.service.task.AgentTaskService} 的验收/打回入口，
 * 不在回调层自行修改任务状态。</p>
 *
 * @author TaskForge
 */
public interface AgentNoticeCallbackService {

    /**
     * 处理一次卡片验收/打回回调。
     *
     * @param request 回调请求（包含任务编号、动作、报告版本、反馈与令牌）
     * @return 处理结果，含成功标记与是否可重试
     */
    AgentNoticeCallbackRespVO handle(AgentNoticeCallbackReqVO request);

}
