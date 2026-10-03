package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskOperationRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskPageReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskSubmitRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskUpdateDocumentReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskUpdateDocumentRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskProjectMapper;
import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocument;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocumentParser;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocumentSectionValidator;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocumentValidator;
import cn.iocoder.yudao.module.agent.service.doc.TaskFrontMatter;
import cn.iocoder.yudao.module.agent.service.scheduler.AgentTaskCancelSignalService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_TASK_NO_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_DOCUMENT_IF_MATCH_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_DOCUMENT_IF_MATCH_MISMATCH;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_DOCUMENT_IF_MATCH_REQUIRED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_DOCUMENT_STATE_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_DOC_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_NOT_FOUND;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_REJECT_FEEDBACK_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_REJECT_FEEDBACK_REQUIRED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_STATUS_TRANSITION_NOT_ALLOWED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_SUBMIT_IDEMPOTENCY_KEY_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_SUBMIT_IDEMPOTENCY_KEY_REQUIRED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_SUBMIT_TASK_NO_DUPLICATE;

/**
 * 任务控制面 Service 实现类
 *
 * <p>负责任务文档投递：校验幂等键、解析并校验文档、生成任务编号、保存初始
 * 文档版本，并通过操作审计表的请求幂等键唯一约束去重，保证重复投递不会重复建任务。
 *
 * @author TaskForge
 */
@Service
@Validated
public class AgentTaskServiceImpl implements AgentTaskService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PAUSED = "PAUSED";
    private static final String ACTION_SUBMIT = "SUBMIT";
    private static final String ACTION_EDIT = "EDIT";
    private static final String MERGE_STATUS_UNMERGED = "UNMERGED";
    private static final int DEFAULT_PRIORITY = 100;
    private static final int MAX_TASK_NO_LENGTH = 64;
    private static final String CLONE_TASK_NO_SUFFIX = "-CLONE-";
    private static final Set<AgentTaskStatus> CLONEABLE_STATUSES =
            EnumSet.of(AgentTaskStatus.CANCELED, AgentTaskStatus.REJECTED, AgentTaskStatus.FAILED);
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{16,128}$");
    private static final Pattern TASK_NO_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");

    @Resource
    private TaskDocumentParser documentParser;

    @Resource
    private TaskDocumentValidator documentValidator;

    @Resource
    private TaskDocumentSectionValidator sectionValidator;

    @Resource
    private AgentTaskMapper taskMapper;

    @Resource
    private AgentTaskOperationLogMapper operationLogMapper;

    @Resource
    private AgentTaskProjectMapper taskProjectMapper;

    @Resource
    private AgentTaskStateMachine stateMachine;

    @Resource
    private AgentTaskCancelSignalService cancelSignalService;

    @Override
    public AgentTaskSubmitRespVO submit(String document, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);

        // 1. 幂等键快速命中：同一请求已投递过，直接返回第一次创建的任务
        AgentTaskOperationLogDO existingLog = operationLogMapper.selectByRequestKey(idempotencyKey);
        if (existingLog != null) {
            AgentTaskDO existingTask = taskMapper.selectById(existingLog.getTaskId());
            if (existingTask != null) {
                return buildResponse(existingTask, existingLog);
            }
        }

        TaskDocument parsed = documentParser.parse(document);
        documentValidator.validate(parsed);
        sectionValidator.validate(parsed);

        TaskFrontMatter frontMatter = parsed.getFrontMatter();
        String taskNo = normalizeTaskNo(frontMatter.getTaskId());

        // 2. 任务编号天然唯一：不同请求携带相同 taskId 属于业务冲突
        if (taskMapper.selectByTaskNo(taskNo) != null) {
            throw exception(TASK_SUBMIT_TASK_NO_DUPLICATE, taskNo);
        }

        // 3. 创建任务并保存初始文档版本
        AgentTaskDO task = buildTask(parsed, taskNo, document);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            // 并发下 task_no 已被占用，按冲突处理
            throw exception(TASK_SUBMIT_TASK_NO_DUPLICATE, taskNo);
        }

        // 4. 记录投递审计；幂等键唯一约束在并发重复投递时兜底
        AgentTaskOperationLogDO operationLog = buildSubmitLog(task.getId(), idempotencyKey);
        try {
            operationLogMapper.insert(operationLog);
        } catch (DuplicateKeyException ex) {
            // 并发下同一幂等键已经由另一请求落库：回滚本次创建的任务并返回胜者
            taskMapper.deleteById(task.getId());
            AgentTaskOperationLogDO winnerLog = operationLogMapper.selectByRequestKey(idempotencyKey);
            if (winnerLog == null) {
                throw ex;
            }
            AgentTaskDO winnerTask = taskMapper.selectById(winnerLog.getTaskId());
            if (winnerTask == null) {
                throw ex;
            }
            return buildResponse(winnerTask, winnerLog);
        }

        return buildResponse(task, operationLog);
    }

    @Override
    public AgentTaskDO getTask(Long id) {
        return taskMapper.selectById(id);
    }

    @Override
    public PageResult<AgentTaskDO> getTaskPage(AgentTaskPageReqVO pageReqVO) {
        return taskMapper.selectPage(pageReqVO);
    }

    @Override
    public AgentTaskUpdateDocumentRespVO updateDocument(Long id,
                                                        AgentTaskUpdateDocumentReqVO reqVO,
                                                        String idempotencyKey,
                                                        String ifMatch) {
        validateIdempotencyKey(idempotencyKey);
        validateIfMatch(ifMatch, reqVO.getDocVersion());

        // 1. 幂等键快速命中：同一任务 + 同一幂等键已执行过，直接返回第一次编辑结果
        AgentTaskOperationLogDO existingLog = operationLogMapper.selectByTaskIdAndRequestKey(id, idempotencyKey);
        if (existingLog != null) {
            AgentTaskDO existingTask = taskMapper.selectById(id);
            if (existingTask != null) {
                return buildUpdateResponse(existingTask, existingLog);
            }
        }

        AgentTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw exception(TASK_NOT_FOUND);
        }
        if (!STATUS_PAUSED.equals(task.getStatus())) {
            throw exception(TASK_DOCUMENT_STATE_INVALID);
        }
        if (!task.getDocVersion().equals(reqVO.getDocVersion())) {
            throw exception(TASK_DOC_VERSION_CONFLICT, task.getDocVersion());
        }

        // 2. 条件更新，以 docVersion 乐观锁防止覆盖他人修改
        int updated = taskMapper.updateDocumentIfVersionMatches(
                id, reqVO.getDocVersion(), reqVO.getDocument(),
                reqVO.getTimeoutMinutes(), reqVO.getPriority(), reqVO.getDependsOnTaskId());
        if (updated == 0) {
            AgentTaskDO current = taskMapper.selectById(id);
            if (current == null) {
                throw exception(TASK_NOT_FOUND);
            }
            if (!STATUS_PAUSED.equals(current.getStatus())) {
                throw exception(TASK_DOCUMENT_STATE_INVALID);
            }
            throw exception(TASK_DOC_VERSION_CONFLICT, current.getDocVersion());
        }

        // 3. 写入 EDIT 审计日志；并发重复幂等键时返回胜者
        Integer newVersion = reqVO.getDocVersion() + 1;
        AgentTaskOperationLogDO operationLog = buildEditLog(id, idempotencyKey, newVersion);
        try {
            operationLogMapper.insert(operationLog);
        } catch (DuplicateKeyException ex) {
            AgentTaskOperationLogDO winner = operationLogMapper.selectByTaskIdAndRequestKey(id, idempotencyKey);
            if (winner == null) {
                winner = operationLogMapper.selectByRequestKey(idempotencyKey);
            }
            if (winner != null && id.equals(winner.getTaskId())) {
                AgentTaskDO winnerTask = taskMapper.selectById(id);
                if (winnerTask != null) {
                    return buildUpdateResponse(winnerTask, winner);
                }
            }
            throw ex;
        }

        // 4. 重新读取最新任务，返回权威状态与版本
        AgentTaskDO updatedTask = taskMapper.selectById(id);
        return buildUpdateResponse(updatedTask != null ? updatedTask : task, operationLog);
    }

    @Override
    public AgentTaskOperationRespVO pause(Long id, String idempotencyKey) {
        return doLifecycleTransition(id, AgentTaskAction.PAUSE, null, idempotencyKey);
    }

    @Override
    public AgentTaskOperationRespVO resume(Long id, String idempotencyKey) {
        return doLifecycleTransition(id, AgentTaskAction.RESUME, null, idempotencyKey);
    }

    @Override
    public AgentTaskOperationRespVO cancel(Long id, String cancelReason, String idempotencyKey) {
        AgentTaskOperationRespVO response =
                doLifecycleTransition(id, AgentTaskAction.CANCEL, cancelReason, idempotencyKey);
        // 状态转换提交后广播取消信号，通知仍持有旧代次的 Worker 立即停止写入
        AgentTaskDO task = taskMapper.selectById(id);
        if (task != null && task.getExecutionGeneration() != null) {
            cancelSignalService.publish(id, task.getExecutionGeneration());
        }
        return response;
    }

    @Override
    public AgentTaskOperationRespVO reEnqueue(Long id, String idempotencyKey) {
        return doLifecycleTransition(id, AgentTaskAction.RE_ENQUEUE, null, idempotencyKey);
    }

    @Override
    public AgentTaskOperationRespVO reEnqueueClone(Long id, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);

        // 1. 幂等键快速命中：同一幂等键已克隆重投过，直接返回第一次克隆结果
        AgentTaskOperationLogDO existingLog = operationLogMapper.selectByRequestKey(idempotencyKey);
        if (existingLog != null && AgentTaskAction.CLONE_RE_ENQUEUE.getValue().equals(existingLog.getAction())) {
            AgentTaskDO existingTask = taskMapper.selectById(existingLog.getTaskId());
            if (existingTask != null) {
                return buildOperationResponse(existingTask, existingLog);
            }
        }

        // 2. 读取原任务并校验其处于可克隆的终态；原任务保持原状态不变
        AgentTaskDO original = taskMapper.selectById(id);
        if (original == null) {
            throw exception(TASK_NOT_FOUND);
        }
        AgentTaskStatus fromStatus = AgentTaskStatus.valueOfCode(original.getStatus());
        if (fromStatus == null || !CLONEABLE_STATUSES.contains(fromStatus)) {
            throw exception(TASK_STATUS_TRANSITION_NOT_ALLOWED, original.getStatus(),
                    AgentTaskAction.CLONE_RE_ENQUEUE.getValue());
        }

        // 3. 生成全新任务编号与独立特性分支，并同步任务文档 Front Matter 的标识
        String newTaskNo = nextCloneTaskNo(original.getTaskNo());
        String newTargetBranch = deriveCloneTargetBranch(original.getTargetBranch(), original.getTaskNo(), newTaskNo);
        String newTaskDoc = rewriteFrontMatterIdentity(original.getTaskDoc(), newTaskNo, newTargetBranch);
        AgentTaskDO clone = AgentTaskDO.builder()
                .taskNo(newTaskNo)
                .title(original.getTitle())
                .status(STATUS_PENDING)
                .priority(original.getPriority())
                .taskDoc(newTaskDoc)
                .docVersion(1)
                .dependsOnTaskId(original.getDependsOnTaskId())
                .targetBranch(newTargetBranch)
                .timeoutMinutes(original.getTimeoutMinutes())
                .retryTimes(0)
                .costMs(0L)
                .executionGeneration(0L)
                .build();
        try {
            taskMapper.insert(clone);
        } catch (DuplicateKeyException ex) {
            throw exception(TASK_SUBMIT_TASK_NO_DUPLICATE, newTaskNo);
        }

        // 4. 复制任务-项目引用；合并状态重置，提交哈希清空
        List<AgentTaskProjectDO> projects = taskProjectMapper.selectListByTaskId(id);
        for (AgentTaskProjectDO project : projects) {
            taskProjectMapper.insert(AgentTaskProjectDO.builder()
                    .taskId(clone.getId())
                    .projectId(project.getProjectId())
                    .projectCode(project.getProjectCode())
                    .baseBranch(project.getBaseBranch())
                    .subDir(project.getSubDir())
                    .mergeStatus(MERGE_STATUS_UNMERGED)
                    .build());
        }

        // 5. 记录克隆重投审计，与重投（RE_ENQUEUE）区分，并保留原任务编号
        AgentTaskOperationLogDO operationLog = buildCloneReEnqueueLog(clone.getId(), fromStatus, idempotencyKey, original);
        try {
            operationLogMapper.insert(operationLog);
        } catch (DuplicateKeyException ex) {
            AgentTaskOperationLogDO winner = operationLogMapper.selectByRequestKey(idempotencyKey);
            if (winner != null) {
                AgentTaskDO winnerTask = taskMapper.selectById(winner.getTaskId());
                if (winnerTask != null) {
                    return buildOperationResponse(winnerTask, winner);
                }
            }
            throw ex;
        }

        return buildOperationResponse(clone, operationLog);
    }

    @Override
    public AgentTaskOperationRespVO deleteTask(Long id, String idempotencyKey) {
        return doLifecycleTransition(id, AgentTaskAction.DELETE, null, idempotencyKey);
    }

    @Override
    public AgentTaskOperationRespVO accept(Long id, String idempotencyKey) {
        return doLifecycleTransition(id, AgentTaskAction.ACCEPT, null, idempotencyKey);
    }

    @Override
    public AgentTaskOperationRespVO reject(Long id, String feedback, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        validateFeedback(feedback);
        return doTransition(id, AgentTaskAction.REJECT, null, feedback, idempotencyKey);
    }

    @Override
    public AgentTaskOperationRespVO markMergeConflict(Long id, String idempotencyKey) {
        return doLifecycleTransition(id, AgentTaskAction.MERGE_CONFLICT, null, idempotencyKey);
    }

    /**
     * 生命周期动作统一入口：幂等重放、读取真实状态、交由状态机执行条件更新与审计。
     */
    private AgentTaskOperationRespVO doLifecycleTransition(Long id, AgentTaskAction action,
                                                           String reason, String idempotencyKey) {
        return doTransition(id, action, reason, null, idempotencyKey);
    }

    private AgentTaskOperationRespVO doTransition(Long id, AgentTaskAction action,
                                                  String cancelReason, String feedback,
                                                  String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);

        // 1. 幂等键快速命中：同一任务 + 同一幂等键已执行过，直接返回第一次结果
        AgentTaskOperationLogDO existingLog = operationLogMapper.selectByTaskIdAndRequestKey(id, idempotencyKey);
        if (existingLog != null) {
            AgentTaskDO existingTask = taskMapper.selectById(id);
            if (existingTask != null) {
                return buildOperationResponse(existingTask, existingLog);
            }
        }

        // 2. 读取真实状态，避免信任前端传入状态
        AgentTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw exception(TASK_NOT_FOUND);
        }

        // 3. 组装转换命令并交给状态机，非法状态由状态机返回业务错误且不落任何数据
        AgentTaskTransitionCommand command = AgentTaskTransitionCommand.builder()
                .taskId(id)
                .taskNo(task.getTaskNo())
                .action(action)
                .fromStatus(AgentTaskStatus.valueOfCode(task.getStatus()))
                .cancelReason(cancelReason)
                .feedback(feedback)
                .docVersion(task.getDocVersion())
                .requestIdempotencyKey(idempotencyKey)
                .build();
        stateMachine.transition(command);

        // 4. 重新读取最新任务与审计记录，返回权威结果
        AgentTaskDO updatedTask = taskMapper.selectById(id);
        AgentTaskOperationLogDO operationLog = operationLogMapper.selectByTaskIdAndRequestKey(id, idempotencyKey);
        return buildOperationResponse(updatedTask != null ? updatedTask : task, operationLog);
    }

    private void validateFeedback(String feedback) {
        if (feedback == null || feedback.isBlank()) {
            throw exception(TASK_REJECT_FEEDBACK_REQUIRED);
        }
        if (feedback.length() > 2000) {
            throw exception(TASK_REJECT_FEEDBACK_INVALID);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw exception(TASK_SUBMIT_IDEMPOTENCY_KEY_REQUIRED);
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw exception(TASK_SUBMIT_IDEMPOTENCY_KEY_INVALID);
        }
    }

    private String normalizeTaskNo(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw exception(DOCUMENT_TASK_NO_INVALID, taskId);
        }
        String value = taskId.trim();
        if (!TASK_NO_PATTERN.matcher(value).matches()) {
            throw exception(DOCUMENT_TASK_NO_INVALID, value);
        }
        return value;
    }

    private AgentTaskDO buildTask(TaskDocument parsed, String taskNo, String document) {
        TaskFrontMatter frontMatter = parsed.getFrontMatter();
        Integer priority = frontMatter.getPriority() == null ? DEFAULT_PRIORITY : frontMatter.getPriority();
        return AgentTaskDO.builder()
                .taskNo(taskNo)
                .title(frontMatter.getTitle())
                .status(STATUS_PENDING)
                .priority(priority)
                .taskDoc(document)
                .docVersion(1)
                .dependsOnTaskId(frontMatter.getDependsOnTaskId())
                .targetBranch(frontMatter.getTargetBranch())
                .timeoutMinutes(frontMatter.getTimeoutMinutes())
                .retryTimes(0)
                .costMs(0L)
                .executionGeneration(0L)
                .build();
    }

    private AgentTaskOperationLogDO buildSubmitLog(Long taskId, String idempotencyKey) {
        return AgentTaskOperationLogDO.builder()
                .taskId(taskId)
                .action(ACTION_SUBMIT)
                .fromStatus(null)
                .toStatus(STATUS_PENDING)
                .docVersion(1)
                .requestIdempotencyKey(idempotencyKey)
                .operatorId(SecurityFrameworkUtils.getLoginUserId())
                .operatorName(SecurityFrameworkUtils.getLoginUserNickname())
                .payload(null)
                .createTime(LocalDateTime.now())
                .build();
    }

    private AgentTaskSubmitRespVO buildResponse(AgentTaskDO task, AgentTaskOperationLogDO operationLog) {
        AgentTaskSubmitRespVO response = new AgentTaskSubmitRespVO();
        response.setTaskId(task.getId());
        response.setTaskNo(task.getTaskNo());
        response.setStatus(task.getStatus());
        response.setDocVersion(task.getDocVersion());
        response.setExecutionGeneration(task.getExecutionGeneration());
        response.setOperationId("op_" + operationLog.getId());
        return response;
    }

    private void validateIfMatch(String ifMatch, Integer expectedVersion) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw exception(TASK_DOCUMENT_IF_MATCH_REQUIRED);
        }
        Integer parsed;
        try {
            parsed = Integer.valueOf(ifMatch.trim());
        } catch (NumberFormatException ex) {
            throw exception(TASK_DOCUMENT_IF_MATCH_INVALID);
        }
        if (parsed < 1) {
            throw exception(TASK_DOCUMENT_IF_MATCH_INVALID);
        }
        if (!parsed.equals(expectedVersion)) {
            throw exception(TASK_DOCUMENT_IF_MATCH_MISMATCH);
        }
    }

    private AgentTaskOperationLogDO buildEditLog(Long taskId, String idempotencyKey, Integer docVersion) {
        return AgentTaskOperationLogDO.builder()
                .taskId(taskId)
                .action(ACTION_EDIT)
                .fromStatus(STATUS_PAUSED)
                .toStatus(STATUS_PAUSED)
                .docVersion(docVersion)
                .requestIdempotencyKey(idempotencyKey)
                .operatorId(SecurityFrameworkUtils.getLoginUserId())
                .operatorName(SecurityFrameworkUtils.getLoginUserNickname())
                .payload(null)
                .createTime(LocalDateTime.now())
                .build();
    }

    private AgentTaskUpdateDocumentRespVO buildUpdateResponse(AgentTaskDO task, AgentTaskOperationLogDO operationLog) {
        AgentTaskUpdateDocumentRespVO response = new AgentTaskUpdateDocumentRespVO();
        response.setTaskId(task.getId());
        response.setTaskNo(task.getTaskNo());
        response.setStatus(task.getStatus());
        response.setDocVersion(task.getDocVersion());
        response.setExecutionGeneration(task.getExecutionGeneration());
        response.setOperationId("op_" + operationLog.getId());
        return response;
    }

    private AgentTaskOperationRespVO buildOperationResponse(AgentTaskDO task, AgentTaskOperationLogDO operationLog) {
        AgentTaskOperationRespVO response = new AgentTaskOperationRespVO();
        response.setTaskId(task.getId());
        response.setTaskNo(task.getTaskNo());
        response.setStatus(task.getStatus());
        response.setDocVersion(task.getDocVersion());
        response.setExecutionGeneration(task.getExecutionGeneration());
        response.setOperationId(operationLog == null ? null : "op_" + operationLog.getId());
        return response;
    }

    private AgentTaskOperationLogDO buildCloneReEnqueueLog(Long cloneId, AgentTaskStatus fromStatus,
                                                           String idempotencyKey, AgentTaskDO original) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceTaskId", original.getId());
        payload.put("sourceTaskNo", original.getTaskNo());
        return AgentTaskOperationLogDO.builder()
                .taskId(cloneId)
                .action(AgentTaskAction.CLONE_RE_ENQUEUE.getValue())
                .fromStatus(fromStatus.getValue())
                .toStatus(STATUS_PENDING)
                .docVersion(1)
                .requestIdempotencyKey(idempotencyKey)
                .operatorId(SecurityFrameworkUtils.getLoginUserId())
                .operatorName(SecurityFrameworkUtils.getLoginUserNickname())
                .payload(JsonUtils.toJsonString(payload))
                .createTime(LocalDateTime.now())
                .build();
    }

    private String nextCloneTaskNo(String sourceTaskNo) {
        for (int sequence = 1; ; sequence++) {
            String candidate = cloneTaskNo(sourceTaskNo, sequence);
            if (taskMapper.selectByTaskNo(candidate) == null) {
                return candidate;
            }
        }
    }

    private String cloneTaskNo(String sourceTaskNo, int sequence) {
        String suffix = CLONE_TASK_NO_SUFFIX + sequence;
        int maxBaseLength = Math.max(1, MAX_TASK_NO_LENGTH - suffix.length());
        String base = sourceTaskNo == null ? "" : sourceTaskNo;
        if (base.length() > maxBaseLength) {
            base = base.substring(0, maxBaseLength);
        }
        return base + suffix;
    }

    private String deriveCloneTargetBranch(String originalTargetBranch, String originalTaskNo, String newTaskNo) {
        if (originalTargetBranch == null || originalTargetBranch.isBlank()) {
            return originalTargetBranch;
        }
        if (originalTaskNo != null && originalTargetBranch.contains(originalTaskNo)) {
            return originalTargetBranch.replace(originalTaskNo, newTaskNo);
        }
        return originalTargetBranch + "-CLONE";
    }

    private String rewriteFrontMatterIdentity(String document, String newTaskNo, String newTargetBranch) {
        if (document == null || document.isBlank()) {
            return document;
        }
        String newline = document.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = document.split("\\r?\\n", -1);
        int opening = -1;
        int closing = -1;
        for (int i = 0; i < lines.length; i++) {
            if ("---".equals(lines[i].trim())) {
                if (opening < 0) {
                    opening = i;
                } else if (closing < 0) {
                    closing = i;
                    break;
                }
            }
        }
        if (opening < 0 || closing < 0 || closing <= opening + 1) {
            return document;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > opening && i < closing) {
                line = rewriteScalar(line, "taskId", newTaskNo);
                line = rewriteScalar(line, "targetBranch", newTargetBranch);
            }
            builder.append(line);
            if (i < lines.length - 1) {
                builder.append(newline);
            }
        }
        return builder.toString();
    }

    private String rewriteScalar(String line, String key, String value) {
        int colon = findKeyColon(line, key);
        if (colon < 0) {
            return line;
        }
        String before = line.substring(0, colon + 1);
        String rest = line.substring(colon + 1);
        int i = 0;
        while (i < rest.length() && (rest.charAt(i) == ' ' || rest.charAt(i) == '\t')) {
            i++;
        }
        String indent = rest.substring(0, i);
        return before + indent + "\"" + value + "\"";
    }

    private int findKeyColon(String line, String key) {
        String trimmed = line.trim();
        if (!trimmed.startsWith(key)) {
            return -1;
        }
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ':') {
                String candidateKey = line.substring(0, i).trim();
                if (key.equals(candidateKey)) {
                    return i;
                }
            }
        }
        return -1;
    }

}
