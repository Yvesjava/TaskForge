package cn.iocoder.yudao.module.agent.service.notice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.controller.admin.notice.vo.AgentNoticeCallbackReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.notice.vo.AgentNoticeCallbackRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskOperationRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.agent.framework.webhook.WebhookCallbackToken;
import cn.iocoder.yudao.module.agent.service.task.AgentTaskService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Locale;

/**
 * 卡片验收/打回回调 Service 实现
 *
 * <p>回调令牌绑定任务编号、动作与报告版本，通过后按任务编号解析主键，并复用控制面
 * {@link AgentTaskService#accept} / {@link AgentTaskService#reject}。令牌自身确定且唯一，
 * 因此作为本次回调的幂等键传给控制面，重复投递由控制面幂等键命中直接返回首次结果。</p>
 *
 * @author TaskForge
 */
@Service
@Validated
public class AgentNoticeCallbackServiceImpl implements AgentNoticeCallbackService {

    private static final String ACTION_ACCEPT = "accept";
    private static final String ACTION_REJECT = "reject";

    @Resource
    private WebhookCallbackToken callbackToken;

    @Resource
    private AgentTaskService taskService;

    @Resource
    private AgentTaskMapper taskMapper;

    @Override
    public AgentNoticeCallbackRespVO handle(AgentNoticeCallbackReqVO request) {
        if (request == null) {
            return rejected(false, null, null, "回调请求体不能为空");
        }

        String action = normalizeAction(request.getAction());
        if (action == null) {
            return rejected(false, request.getTaskNo(), null, "不支持的回调动作：" + request.getAction());
        }

        if (!callbackToken.matches(request.getToken(), request.getTaskNo(), action, request.getReportVersion())) {
            return rejected(false, request.getTaskNo(), null, "回调令牌无效或与任务、动作不匹配");
        }

        AgentTaskDO task = taskMapper.selectByTaskNo(request.getTaskNo());
        if (task == null) {
            return rejected(false, request.getTaskNo(), null, "任务不存在：" + request.getTaskNo());
        }

        // 非待验收说明任务已进入终态：重复点击只返回当前结果，不再推进状态
        if (!AgentTaskStatus.WAITING_ACCEPTANCE.getValue().equals(task.getStatus())) {
            return success(task, null, "任务已处于终态，忽略重复回调");
        }

        // 令牌绑定 taskNo + action + reportVersion，确定性且不可逆，天然可作为幂等键
        String idempotencyKey = request.getToken();
        try {
            AgentTaskOperationRespVO operation = ACTION_ACCEPT.equals(action)
                    ? taskService.accept(task.getId(), idempotencyKey)
                    : taskService.reject(task.getId(), request.getFeedback(), idempotencyKey);
            return success(operation, "回调处理成功");
        } catch (ServiceException ex) {
            if (isTransitionConflict(ex)) {
                AgentTaskDO current = taskMapper.selectById(task.getId());
                return success(current, null, "任务状态已变化，返回当前结果");
            }
            return rejected(false, task.getTaskNo(), task.getStatus(), ex.getMessage());
        } catch (Exception ex) {
            return rejected(true, task.getTaskNo(), task.getStatus(), "回调处理失败：" + reasonOf(ex));
        }
    }

    private static String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        String value = action.trim().toLowerCase(Locale.ROOT);
        if (ACTION_ACCEPT.equals(value)) {
            return ACTION_ACCEPT;
        }
        if (ACTION_REJECT.equals(value)) {
            return ACTION_REJECT;
        }
        return null;
    }

    private static boolean isTransitionConflict(ServiceException ex) {
        Integer code = ex.getCode();
        return code != null
                && (code.equals(ErrorCodeConstants.TASK_STATUS_TRANSITION_NOT_ALLOWED.getCode())
                || code.equals(ErrorCodeConstants.TASK_STATUS_UPDATE_CONFLICT.getCode()));
    }

    private static AgentNoticeCallbackRespVO success(AgentTaskDO task, String operationId, String message) {
        AgentNoticeCallbackRespVO response = new AgentNoticeCallbackRespVO();
        response.setSuccess(true);
        response.setRetryable(false);
        response.setTaskNo(task == null ? null : task.getTaskNo());
        response.setStatus(task == null ? null : task.getStatus());
        response.setOperationId(operationId);
        response.setMessage(message);
        return response;
    }

    private static AgentNoticeCallbackRespVO success(AgentTaskOperationRespVO operation, String message) {
        AgentNoticeCallbackRespVO response = new AgentNoticeCallbackRespVO();
        response.setSuccess(true);
        response.setRetryable(false);
        response.setTaskNo(operation.getTaskNo());
        response.setStatus(operation.getStatus());
        response.setOperationId(operation.getOperationId());
        response.setMessage(message);
        return response;
    }

    private static AgentNoticeCallbackRespVO rejected(boolean retryable, String taskNo, String status, String message) {
        AgentNoticeCallbackRespVO response = new AgentNoticeCallbackRespVO();
        response.setSuccess(false);
        response.setRetryable(retryable);
        response.setTaskNo(taskNo);
        response.setStatus(status);
        response.setMessage(message);
        return response;
    }

    private static String reasonOf(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

}
