package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskUpdateDocumentReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskUpdateDocumentRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-DOC-05 文档编辑与 docVersion 乐观锁的单元测试。
 *
 * <p>覆盖成功编辑并写入操作日志、版本冲突不覆盖他人修改，以及状态与 If-Match 校验。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskDocVersionTest {

    private static final Long TASK_ID = 9012L;
    private static final String IDEMPOTENCY_KEY = "req_20261003_0002";

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskOperationLogMapper operationLogMapper;

    @InjectMocks
    private AgentTaskServiceImpl taskService;

    @Test
    void updateDocument_success_updatesTaskAndWritesEditLog() {
        AgentTaskDO current = pausedTask(1);
        AgentTaskDO updated = pausedTask(2);
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectById(TASK_ID)).thenReturn(current, updated);
        when(taskMapper.updateDocumentIfVersionMatches(eq(TASK_ID), eq(1), eq("edited doc"), eq(60), eq(50), eq(8001L)))
                .thenReturn(1);
        doAnswer(invocation -> {
            AgentTaskOperationLogDO log = invocation.getArgument(0);
            log.setId(88L);
            return 1;
        }).when(operationLogMapper).insert(any(AgentTaskOperationLogDO.class));

        AgentTaskUpdateDocumentRespVO response = taskService.updateDocument(
                TASK_ID, updateReq(1, "edited doc", 60, 50, 8001L), IDEMPOTENCY_KEY, "1");

        assertThat(response.getTaskId()).isEqualTo(TASK_ID);
        assertThat(response.getTaskNo()).isEqualTo("TASK-20261001-088");
        assertThat(response.getStatus()).isEqualTo("PAUSED");
        assertThat(response.getDocVersion()).isEqualTo(2);
        assertThat(response.getOperationId()).isEqualTo("op_88");

        ArgumentCaptor<AgentTaskOperationLogDO> logCaptor = ArgumentCaptor.forClass(AgentTaskOperationLogDO.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        AgentTaskOperationLogDO log = logCaptor.getValue();
        assertThat(log.getTaskId()).isEqualTo(TASK_ID);
        assertThat(log.getAction()).isEqualTo("EDIT");
        assertThat(log.getFromStatus()).isEqualTo("PAUSED");
        assertThat(log.getToStatus()).isEqualTo("PAUSED");
        assertThat(log.getDocVersion()).isEqualTo(2);
        assertThat(log.getRequestIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    void updateDocument_versionConflict_doesNotOverwrite() {
        AgentTaskDO current = pausedTask(2);
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectById(TASK_ID)).thenReturn(current);

        assertThatThrownBy(() -> taskService.updateDocument(
                TASK_ID, updateReq(1, "stale edit", 60, 50, null), IDEMPOTENCY_KEY, "1"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_DOC_VERSION_CONFLICT.getCode()));

        verify(taskMapper, never()).updateDocumentIfVersionMatches(anyLong(), any(), anyString(), any(), any(), any());
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    @Test
    void updateDocument_notPaused_throwsStateError() {
        AgentTaskDO pending = AgentTaskDO.builder()
                .id(TASK_ID).taskNo("TASK-20261001-088").status("PENDING").docVersion(1).executionGeneration(0L).build();
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pending);

        assertThatThrownBy(() -> taskService.updateDocument(
                TASK_ID, updateReq(1, "edited doc", 60, 50, null), IDEMPOTENCY_KEY, "1"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_DOCUMENT_STATE_INVALID.getCode()));
        verify(taskMapper, never()).updateDocumentIfVersionMatches(anyLong(), any(), anyString(), any(), any(), any());
    }

    @Test
    void updateDocument_ifMatchMismatch_throws() {
        assertThatThrownBy(() -> taskService.updateDocument(
                TASK_ID, updateReq(1, "edited doc", 60, 50, null), IDEMPOTENCY_KEY, "2"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_DOCUMENT_IF_MATCH_MISMATCH.getCode()));
        verify(taskMapper, never()).selectById(anyLong());
    }

    @Test
    void updateDocument_idempotentReplay_returnsFirstResult() {
        AgentTaskOperationLogDO existingLog = AgentTaskOperationLogDO.builder()
                .id(88L).taskId(TASK_ID).action("EDIT").docVersion(2).requestIdempotencyKey(IDEMPOTENCY_KEY).build();
        AgentTaskDO updated = pausedTask(2);
        when(operationLogMapper.selectByTaskIdAndRequestKey(TASK_ID, IDEMPOTENCY_KEY)).thenReturn(existingLog);
        when(taskMapper.selectById(TASK_ID)).thenReturn(updated);

        AgentTaskUpdateDocumentRespVO response = taskService.updateDocument(
                TASK_ID, updateReq(2, "edited doc", 60, 50, null), IDEMPOTENCY_KEY, "2");

        assertThat(response.getDocVersion()).isEqualTo(2);
        assertThat(response.getOperationId()).isEqualTo("op_88");
        verify(taskMapper, never()).updateDocumentIfVersionMatches(anyLong(), any(), anyString(), any(), any(), any());
        verify(operationLogMapper, never()).insert(any(AgentTaskOperationLogDO.class));
    }

    private static AgentTaskDO pausedTask(int docVersion) {
        return AgentTaskDO.builder()
                .id(TASK_ID)
                .taskNo("TASK-20261001-088")
                .status("PAUSED")
                .docVersion(docVersion)
                .executionGeneration(0L)
                .build();
    }

    private static AgentTaskUpdateDocumentReqVO updateReq(Integer docVersion, String document,
                                                         Integer timeoutMinutes, Integer priority,
                                                         Long dependsOnTaskId) {
        AgentTaskUpdateDocumentReqVO reqVO = new AgentTaskUpdateDocumentReqVO();
        reqVO.setDocVersion(docVersion);
        reqVO.setDocument(document);
        reqVO.setTimeoutMinutes(timeoutMinutes);
        reqVO.setPriority(priority);
        reqVO.setDependsOnTaskId(dependsOnTaskId);
        return reqVO;
    }

}
