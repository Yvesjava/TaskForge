package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskDiffRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskBranchInfoVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentProjectDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentProjectMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskProjectMapper;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link AgentTaskDiffServiceImpl} 的单元测试
 *
 * <p>覆盖分支聚合、测试报告解析、变更文件提取、分支链接推导与空值兜底。
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskDiffServiceTest {

    private static final Long TASK_ID = 9012L;
    private static final String TARGET_BRANCH = "feature/TASK-20261001-088";

    @Mock
    private AgentTaskMapper taskMapper;

    @Mock
    private AgentTaskProjectMapper taskProjectMapper;

    @Mock
    private AgentProjectMapper projectMapper;

    @InjectMocks
    private AgentTaskDiffServiceImpl diffService;

    @TempDir
    Path tempDir;

    @Test
    void getTaskDiff_aggregatesTaskProjectsAndProjects() {
        AgentTaskDO task = task()
                .diffStat("{\"changedFiles\":[\"backend/A.java\",\"frontend/B.vue\"],\"insertions\":3}")
                .executionLog("=== Codex attempts ===\nok\n")
                .build();
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of(
                taskProject("backend-service", "backend", "UNMERGED", "abc1234")));
        when(projectMapper.selectByProjectCode("backend-service")).thenReturn(project(
                "backend-service", "后端服务", "git@github.com:Yvesjava/TaskForge.git"));

        AgentTaskDiffRespVO response = diffService.getTaskDiff(TASK_ID);

        assertThat(response.getTaskId()).isEqualTo(TASK_ID);
        assertThat(response.getTaskNo()).isEqualTo("TASK-20261001-088");
        assertThat(response.getTitle()).isEqualTo("会员充值优惠券抵扣全栈支持");
        assertThat(response.getStatus()).isEqualTo("WAITING_ACCEPTANCE");
        assertThat(response.getTargetBranch()).isEqualTo(TARGET_BRANCH);
        assertThat(response.getDiffStat()).contains("changedFiles");
        assertThat(response.getExecutionLog()).contains("Codex attempts");
        assertThat(response.getLogTruncated()).isFalse();
        assertThat(response.getOriginalLogPath()).isNull();
        assertThat(response.getChangedFiles()).containsExactly("backend/A.java", "frontend/B.vue");

        assertThat(response.getBranches()).hasSize(1);
        AgentTaskBranchInfoVO branch = response.getBranches().get(0);
        assertThat(branch.getProjectCode()).isEqualTo("backend-service");
        assertThat(branch.getProjectName()).isEqualTo("后端服务");
        assertThat(branch.getBaseBranch()).isEqualTo("main");
        assertThat(branch.getFeatureBranch()).isEqualTo(TARGET_BRANCH);
        assertThat(branch.getSubDir()).isEqualTo("backend");
        assertThat(branch.getMergeStatus()).isEqualTo("UNMERGED");
        assertThat(branch.getCommitHash()).isEqualTo("abc1234");
        assertThat(branch.getBranchUrl())
                .isEqualTo("https://github.com/Yvesjava/TaskForge/tree/" + TARGET_BRANCH);
    }

    @Test
    void getTaskDiff_returnsExplicitEmptyValuesWhenNoResultData() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task().build());
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of());

        AgentTaskDiffRespVO response = diffService.getTaskDiff(TASK_ID);

        assertThat(response.getBranches()).isEmpty();
        assertThat(response.getChangedFiles()).isEmpty();
        assertThat(response.getDiffStat()).isNull();
        assertThat(response.getExecutionLog()).isNull();
        assertThat(response.getTestReport()).isNull();
        assertThat(response.getLogTruncated()).isFalse();
        assertThat(response.getOriginalLogPath()).isNull();
    }

    @Test
    void getTaskDiff_loadsTestReportFromWorkpadSummary() throws Exception {
        Path aiDir = Files.createDirectories(tempDir.resolve(".ai"));
        Files.writeString(aiDir.resolve("workpad_summary.json"),
                "{\"allPassed\":true,\"testsExecuted\":[\"mvn test\"],"
                        + "\"modifiedFiles\":[\"backend/A.java\"],\"notes\":\"green\"}",
                StandardCharsets.UTF_8);
        when(taskMapper.selectById(TASK_ID)).thenReturn(
                task().workspacePath(tempDir.toString()).build());
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of());

        AgentTaskDiffRespVO response = diffService.getTaskDiff(TASK_ID);

        assertThat(response.getTestReport()).isNotNull();
        assertThat(response.getTestReport().getAllPassed()).isTrue();
        assertThat(response.getTestReport().getTestsExecuted()).containsExactly("mvn test");
        assertThat(response.getTestReport().getModifiedFiles()).containsExactly("backend/A.java");
        assertThat(response.getTestReport().getNotes()).isEqualTo("green");
        // diffStat 为空时，changedFiles 回退到报告的 modifiedFiles
        assertThat(response.getChangedFiles()).containsExactly("backend/A.java");
    }

    @Test
    void getTaskDiff_extractsChangedFilesFromDiffStatArray() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task()
                .diffStat("[{\"path\":\"backend/A.java\"},{\"file\":\"backend/B.java\"}]")
                .build());
        when(taskProjectMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of());

        AgentTaskDiffRespVO response = diffService.getTaskDiff(TASK_ID);

        assertThat(response.getChangedFiles()).containsExactly("backend/A.java", "backend/B.java");
    }

    @Test
    void getTaskDiff_taskNotFound_throws() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);

        assertThatThrownBy(() -> diffService.getTaskDiff(TASK_ID))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.TASK_NOT_FOUND.getCode()));
    }

    private static AgentTaskDO.AgentTaskDOBuilder task() {
        return AgentTaskDO.builder()
                .id(TASK_ID)
                .taskNo("TASK-20261001-088")
                .title("会员充值优惠券抵扣全栈支持")
                .status("WAITING_ACCEPTANCE")
                .targetBranch(TARGET_BRANCH)
                .retryTimes(1)
                .costMs(12000L)
                .startedTime(LocalDateTime.of(2026, 10, 4, 10, 0))
                .finishedTime(LocalDateTime.of(2026, 10, 4, 10, 1))
                .executionGeneration(1L);
    }

    private static AgentTaskProjectDO taskProject(String projectCode, String subDir,
                                                  String mergeStatus, String commitHash) {
        return AgentTaskProjectDO.builder()
                .taskId(TASK_ID)
                .projectId(1001L)
                .projectCode(projectCode)
                .baseBranch("main")
                .subDir(subDir)
                .mergeStatus(mergeStatus)
                .commitHash(commitHash)
                .build();
    }

    private static AgentProjectDO project(String projectCode, String name, String gitUrl) {
        return AgentProjectDO.builder()
                .id(1001L)
                .projectCode(projectCode)
                .name(name)
                .gitUrl(gitUrl)
                .build();
    }

}
