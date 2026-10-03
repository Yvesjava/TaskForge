package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskOperationRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskProjectMapper;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 克隆重投（re-enqueue clone）专项单元测试。
 *
 * <p>覆盖 {@code CANCELED}/{@code REJECTED}/{@code FAILED} 克隆为全新任务编号、
 * 克隆件与原任务相互独立、文档与项目引用复制、执行痕迹清零、审计区分动作，
 * 以及重复幂等键不重复克隆。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskReEnqueueCloneTest {

    private static final Long ORIGINAL_ID = 9012L;
    private static final Long CLONE_ID = 9100L;
    private static final String ORIGINAL_TASK_NO = "TASK-20261001-088";
    private static final String IDEMPOTENCY_KEY = "req_20261004_clone_001";

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskOperationLogMapper operationLogMapper;

    @Mock
    private AgentTaskProjectMapper taskProjectMapper;

    @InjectMocks
    private AgentTaskServiceImpl taskService;

    @ParameterizedTest
    @ValueSource(strings = {"CANCELED", "REJECTED", "FAILED"})
    void clone_endedTask_createsIndependentPendingClone(String sourceStatus) {
        when(operationLogMapper.selectByRequestKey(IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectById(ORIGINAL_ID)).thenReturn(originalTask(sourceStatus));
        when(taskMapper.selectByTaskNo(anyString())).thenReturn(null);
        doAnswer(invocation -> {
            AgentTaskDO task = invocation.getArgument(0);
            task.setId(CLONE_ID);
            return 1;
        }).when(taskMapper).insert(any(AgentTaskDO.class));
        when(taskProjectMapper.selectListByTaskId(ORIGINAL_ID)).thenReturn(List.of());
        doAnswer(invocation -> {
            AgentTaskOperationLogDO log = invocation.getArgument(0);
            log.setId(200L);
            return 1;
        }).when(operationLogMapper).insert(any(AgentTaskOperationLogDO.class));

        AgentTaskOperationRespVO response = taskService.reEnqueueClone(ORIGINAL_ID, IDEMPOTENCY_KEY);

        assertThat(response.getTaskId()).isEqualTo(CLONE_ID);
        assertThat(response.getTaskNo()).startsWith(ORIGINAL_TASK_NO + "-CLONE-");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getDocVersion()).isEqualTo(1);
        assertThat(response.getExecutionGeneration()).isZero();
        assertThat(response.getOperationId()).isEqualTo("op_200");

        ArgumentCaptor<AgentTaskDO> taskCaptor = ArgumentCaptor.forClass(AgentTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        AgentTaskDO clone = taskCaptor.getValue();
        assertThat(clone.getStatus()).isEqualTo("PENDING");
        assertThat(clone.getDocVersion()).isEqualTo(1);
        assertThat(clone.getExecutionGeneration()).isZero();
        assertThat(clone.getRetryTimes()).isZero();
        assertThat(clone.getCostMs()).isZero();
        assertThat(clone.getExecutionLog()).isNull();
        assertThat(clone.getDiffStat()).isNull();
        assertThat(clone.getWorkspacePath()).isNull();
        assertThat(clone.getWorkerId()).isNull();
        assertThat(clone.getCancelReason()).isNull();
        assertThat(clone.getTitle()).isEqualTo("会员充值优惠券抵扣全栈支持");
        // 原任务保持不变：克隆不触发任何针对原任务的条件更新
        verify(taskMapper, never()).reEnqueueIfEnded(any(Long.class));
    }

    @Test
    void clone_copiesDocumentAndProjectRefs_andClearsExecutionState() {
        when(operationLogMapper.selectByRequestKey(IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectById(ORIGINAL_ID)).thenReturn(originalTask("FAILED"));
        when(taskMapper.selectByTaskNo(anyString())).thenReturn(null);
        doAnswer(invocation -> {
            AgentTaskDO task = invocation.getArgument(0);
            task.setId(CLONE_ID);
            return 1;
        }).when(taskMapper).insert(any(AgentTaskDO.class));
        when(taskProjectMapper.selectListByTaskId(ORIGINAL_ID)).thenReturn(List.of(projectRef(7L, "backend")));
        doAnswer(invocation -> {
            AgentTaskOperationLogDO log = invocation.getArgument(0);
            log.setId(201L);
            return 1;
        }).when(operationLogMapper).insert(any(AgentTaskOperationLogDO.class));

        taskService.reEnqueueClone(ORIGINAL_ID, IDEMPOTENCY_KEY);

        ArgumentCaptor<AgentTaskDO> taskCaptor = ArgumentCaptor.forClass(AgentTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        AgentTaskDO clone = taskCaptor.getValue();
        assertThat(clone.getTaskDoc())
                .contains("taskId: \"" + clone.getTaskNo() + "\"")
                .contains("targetBranch: \"feature/" + clone.getTaskNo() + "\"");
        assertThat(clone.getTargetBranch()).isEqualTo("feature/" + clone.getTaskNo());
        assertThat(clone.getExecutionLog()).isNull();
        assertThat(clone.getRetryTimes()).isZero();
        assertThat(clone.getCostMs()).isZero();

        ArgumentCaptor<AgentTaskProjectDO> projectCaptor = ArgumentCaptor.forClass(AgentTaskProjectDO.class);
        verify(taskProjectMapper).insert(projectCaptor.capture());
        AgentTaskProjectDO copied = projectCaptor.getValue();
        assertThat(copied.getTaskId()).isEqualTo(CLONE_ID);
        assertThat(copied.getProjectId()).isEqualTo(7L);
        assertThat(copied.getProjectCode()).isEqualTo("backend");
        assertThat(copied.getBaseBranch()).isEqualTo("main");
        assertThat(copied.getSubDir()).isEqualTo("backend");
        assertThat(copied.getMergeStatus()).isEqualTo("UNMERGED");
        assertThat(copied.getCommitHash()).isNull();
    }

    @Test
    void clone_illegalStatus_throwsConflictWithoutChanges() {
        when(operationLogMapper.selectByRequestKey(IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectById(ORIGINAL_ID)).thenReturn(originalTask("PENDING"));

        assertThatThrownBy(() -> taskService.reEnqueueClone(ORIGINAL_ID, IDEMPOTENCY_KEY))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_STATUS_TRANSITION_NOT_ALLOWED.getCode()));

        verify(taskMapper, never()).insert(any(AgentTaskDO.class));
        verify(taskProjectMapper, never()).insert(any(AgentTaskProjectDO.class));
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    @Test
    void clone_duplicateIdempotencyKey_returnsFirstCloneWithoutSecondInsert() {
        AgentTaskOperationLogDO existingLog = AgentTaskOperationLogDO.builder()
                .id(202L)
                .taskId(CLONE_ID)
                .action("CLONE_RE_ENQUEUE")
                .fromStatus("CANCELED")
                .toStatus("PENDING")
                .requestIdempotencyKey(IDEMPOTENCY_KEY)
                .build();
        when(operationLogMapper.selectByRequestKey(IDEMPOTENCY_KEY)).thenReturn(existingLog);
        when(taskMapper.selectById(CLONE_ID)).thenReturn(cloneTask());

        AgentTaskOperationRespVO response = taskService.reEnqueueClone(ORIGINAL_ID, IDEMPOTENCY_KEY);

        assertThat(response.getTaskId()).isEqualTo(CLONE_ID);
        assertThat(response.getTaskNo()).isEqualTo(ORIGINAL_TASK_NO + "-CLONE-1");
        assertThat(response.getOperationId()).isEqualTo("op_202");
        verify(taskMapper, never()).insert(any(AgentTaskDO.class));
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    @Test
    void clone_generatesNextTaskNo_whenFirstCandidateAlreadyTaken() {
        when(operationLogMapper.selectByRequestKey(IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectById(ORIGINAL_ID)).thenReturn(originalTask("CANCELED"));
        when(taskMapper.selectByTaskNo(ORIGINAL_TASK_NO + "-CLONE-1"))
                .thenReturn(AgentTaskDO.builder().id(1L).taskNo(ORIGINAL_TASK_NO + "-CLONE-1").build());
        when(taskMapper.selectByTaskNo(ORIGINAL_TASK_NO + "-CLONE-2")).thenReturn(null);
        doAnswer(invocation -> {
            AgentTaskDO task = invocation.getArgument(0);
            task.setId(CLONE_ID);
            return 1;
        }).when(taskMapper).insert(any(AgentTaskDO.class));
        when(taskProjectMapper.selectListByTaskId(ORIGINAL_ID)).thenReturn(List.of());
        doAnswer(invocation -> {
            AgentTaskOperationLogDO log = invocation.getArgument(0);
            log.setId(203L);
            return 1;
        }).when(operationLogMapper).insert(any(AgentTaskOperationLogDO.class));

        AgentTaskOperationRespVO response = taskService.reEnqueueClone(ORIGINAL_ID, IDEMPOTENCY_KEY);

        assertThat(response.getTaskNo()).isEqualTo(ORIGINAL_TASK_NO + "-CLONE-2");
    }

    @Test
    void clone_auditLog_distinguishesActionAndKeepsSourceTaskNo() {
        when(operationLogMapper.selectByRequestKey(IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectById(ORIGINAL_ID)).thenReturn(originalTask("REJECTED"));
        when(taskMapper.selectByTaskNo(anyString())).thenReturn(null);
        doAnswer(invocation -> {
            AgentTaskDO task = invocation.getArgument(0);
            task.setId(CLONE_ID);
            return 1;
        }).when(taskMapper).insert(any(AgentTaskDO.class));
        when(taskProjectMapper.selectListByTaskId(ORIGINAL_ID)).thenReturn(List.of());
        doAnswer(invocation -> {
            AgentTaskOperationLogDO log = invocation.getArgument(0);
            log.setId(204L);
            return 1;
        }).when(operationLogMapper).insert(any(AgentTaskOperationLogDO.class));

        taskService.reEnqueueClone(ORIGINAL_ID, IDEMPOTENCY_KEY);

        ArgumentCaptor<AgentTaskOperationLogDO> logCaptor = ArgumentCaptor.forClass(AgentTaskOperationLogDO.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        AgentTaskOperationLogDO log = logCaptor.getValue();
        assertThat(log.getAction()).isEqualTo("CLONE_RE_ENQUEUE");
        assertThat(log.getFromStatus()).isEqualTo("REJECTED");
        assertThat(log.getToStatus()).isEqualTo("PENDING");
        assertThat(log.getTaskId()).isEqualTo(CLONE_ID);
        assertThat(log.getPayload()).contains("\"sourceTaskNo\":\"" + ORIGINAL_TASK_NO + "\"");
        assertThat(log.getPayload()).contains("\"sourceTaskId\":" + ORIGINAL_ID);
    }

    private AgentTaskDO originalTask(String status) {
        return AgentTaskDO.builder()
                .id(ORIGINAL_ID)
                .taskNo(ORIGINAL_TASK_NO)
                .title("会员充值优惠券抵扣全栈支持")
                .status(status)
                .priority(50)
                .taskDoc("---\n"
                        + "taskId: \"" + ORIGINAL_TASK_NO + "\"\n"
                        + "title: \"会员充值优惠券抵扣全栈支持\"\n"
                        + "targetBranch: \"feature/" + ORIGINAL_TASK_NO + "\"\n"
                        + "timeoutMinutes: 45\n"
                        + "repoUrl: \"git@github.com:Yvesjava/TaskForge.git\"\n"
                        + "baseBranch: \"main\"\n"
                        + "---\n"
                        + "# 需求目标与上下文\n")
                .docVersion(3)
                .dependsOnTaskId(null)
                .targetBranch("feature/" + ORIGINAL_TASK_NO)
                .timeoutMinutes(45)
                .executionLog("previous execution log")
                .diffStat("{\"files\":1}")
                .retryTimes(2)
                .costMs(1500L)
                .executionGeneration(4L)
                .workerId("worker-1")
                .cancelReason("需求变更")
                .build();
    }

    private AgentTaskDO cloneTask() {
        return AgentTaskDO.builder()
                .id(CLONE_ID)
                .taskNo(ORIGINAL_TASK_NO + "-CLONE-1")
                .status("PENDING")
                .docVersion(1)
                .executionGeneration(0L)
                .build();
    }

    private AgentTaskProjectDO projectRef(Long projectId, String projectCode) {
        return AgentTaskProjectDO.builder()
                .id(10L)
                .taskId(ORIGINAL_ID)
                .projectId(projectId)
                .projectCode(projectCode)
                .baseBranch("main")
                .subDir(projectCode)
                .mergeStatus("MERGED")
                .commitHash("abc123")
                .build();
    }

}
