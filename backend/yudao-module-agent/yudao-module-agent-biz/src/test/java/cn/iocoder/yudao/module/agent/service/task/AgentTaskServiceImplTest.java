package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskSubmitRespVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskOperationLogDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskOperationLogMapper;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocument;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocumentParser;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocumentSectionValidator;
import cn.iocoder.yudao.module.agent.service.doc.TaskDocumentValidator;
import cn.iocoder.yudao.module.agent.service.doc.TaskFrontMatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentTaskServiceImpl} 的单元测试
 *
 * <p>覆盖任务编号生成、初始文档版本保存、幂等键去重与任务编号冲突校验。
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskServiceImplTest {

    private static final String IDEMPOTENCY_KEY = "req_20261003_0001";

    @Mock
    private TaskDocumentParser documentParser;

    @Mock
    private TaskDocumentValidator documentValidator;

    @Mock
    private TaskDocumentSectionValidator sectionValidator;

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskOperationLogMapper operationLogMapper;

    @InjectMocks
    private AgentTaskServiceImpl taskService;

    @Test
    void submit_success_createsTaskAndReturnsContract() {
        String document = validDocumentText();
        when(documentParser.parse(document)).thenReturn(validTaskDocument());
        when(operationLogMapper.selectByRequestKey(IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectByTaskNo("TASK-20261001-088")).thenReturn(null);
        doAnswer(invocation -> {
            AgentTaskDO task = invocation.getArgument(0);
            task.setId(9012L);
            return 1;
        }).when(taskMapper).insert(any(AgentTaskDO.class));
        doAnswer(invocation -> {
            AgentTaskOperationLogDO log = invocation.getArgument(0);
            log.setId(77L);
            return 1;
        }).when(operationLogMapper).insert(any(AgentTaskOperationLogDO.class));

        AgentTaskSubmitRespVO response = taskService.submit(document, IDEMPOTENCY_KEY);

        assertThat(response.getTaskId()).isEqualTo(9012L);
        assertThat(response.getTaskNo()).isEqualTo("TASK-20261001-088");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getDocVersion()).isEqualTo(1);
        assertThat(response.getExecutionGeneration()).isZero();
        assertThat(response.getOperationId()).isEqualTo("op_77");

        ArgumentCaptor<AgentTaskDO> taskCaptor = ArgumentCaptor.forClass(AgentTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getTaskNo()).isEqualTo("TASK-20261001-088");
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(taskCaptor.getValue().getDocVersion()).isEqualTo(1);
        assertThat(taskCaptor.getValue().getPriority()).isEqualTo(100);
        assertThat(taskCaptor.getValue().getTaskDoc()).isEqualTo(document);

        ArgumentCaptor<AgentTaskOperationLogDO> logCaptor = ArgumentCaptor.forClass(AgentTaskOperationLogDO.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getTaskId()).isEqualTo(9012L);
        assertThat(logCaptor.getValue().getAction()).isEqualTo("SUBMIT");
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo("PENDING");
        assertThat(logCaptor.getValue().getRequestIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    void submit_duplicateIdempotencyKey_returnsFirstResultWithoutCreating() {
        AgentTaskOperationLogDO existingLog = AgentTaskOperationLogDO.builder()
                .id(77L).taskId(9012L).action("SUBMIT").requestIdempotencyKey(IDEMPOTENCY_KEY).build();
        AgentTaskDO existingTask = AgentTaskDO.builder()
                .id(9012L).taskNo("TASK-20261001-088").status("PENDING")
                .docVersion(1).executionGeneration(0L).build();
        when(operationLogMapper.selectByRequestKey(IDEMPOTENCY_KEY)).thenReturn(existingLog);
        when(taskMapper.selectById(9012L)).thenReturn(existingTask);

        AgentTaskSubmitRespVO response = taskService.submit("changed document", IDEMPOTENCY_KEY);

        assertThat(response.getTaskNo()).isEqualTo("TASK-20261001-088");
        assertThat(response.getOperationId()).isEqualTo("op_77");
        verify(documentParser, never()).parse(anyString());
        verify(taskMapper, never()).insert(any(AgentTaskDO.class));
    }

    @Test
    void submit_missingIdempotencyKey_throws() {
        assertThatThrownBy(() -> taskService.submit(validDocumentText(), " "))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_SUBMIT_IDEMPOTENCY_KEY_REQUIRED.getCode()));
    }

    @Test
    void submit_invalidIdempotencyKey_throws() {
        assertThatThrownBy(() -> taskService.submit(validDocumentText(), "too-short"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_SUBMIT_IDEMPOTENCY_KEY_INVALID.getCode()));
    }

    @Test
    void submit_duplicateTaskNo_throws() {
        String document = validDocumentText();
        when(documentParser.parse(document)).thenReturn(validTaskDocument());
        when(operationLogMapper.selectByRequestKey(IDEMPOTENCY_KEY)).thenReturn(null);
        when(taskMapper.selectByTaskNo("TASK-20261001-088"))
                .thenReturn(AgentTaskDO.builder().id(9001L).taskNo("TASK-20261001-088").build());

        assertThatThrownBy(() -> taskService.submit(document, IDEMPOTENCY_KEY))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_SUBMIT_TASK_NO_DUPLICATE.getCode()));
        verify(taskMapper, never()).insert(any(AgentTaskDO.class));
    }

    private static TaskDocument validTaskDocument() {
        return TaskDocument.builder()
                .frontMatter(TaskFrontMatter.builder()
                        .taskId("TASK-20261001-088")
                        .title("会员充值优惠券抵扣全栈支持")
                        .targetBranch("feature/TASK-20261001-088")
                        .timeoutMinutes(45)
                        .repoUrl("git@github.com:Yvesjava/TaskForge.git")
                        .baseBranch("main")
                        .build())
                .body("# 需求目标与上下文\n")
                .build();
    }

    private static String validDocumentText() {
        return "---\n"
                + "taskId: \"TASK-20261001-088\"\n"
                + "title: \"会员充值优惠券抵扣全栈支持\"\n"
                + "targetBranch: \"feature/TASK-20261001-088\"\n"
                + "timeoutMinutes: 45\n"
                + "repoUrl: \"git@github.com:Yvesjava/TaskForge.git\"\n"
                + "baseBranch: \"main\"\n"
                + "---\n";
    }

}
