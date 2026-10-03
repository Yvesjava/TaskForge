package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskSubmitRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocument;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocumentParser;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocumentSectionValidator;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocumentValidator;
import cn.iocoder.yudao.module.agent.service.doc.TaskFrontMatter;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_TASK_NO_INVALID;
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
    private static final String ACTION_SUBMIT = "SUBMIT";
    private static final int DEFAULT_PRIORITY = 100;
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

}
