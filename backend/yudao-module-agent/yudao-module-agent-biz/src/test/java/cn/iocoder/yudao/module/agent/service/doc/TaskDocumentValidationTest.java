package cn.iocoder.yudao.module.agent.service.doc;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentProjectMapper;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.agent.service.project.AgentProjectRefValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link TaskDocumentValidator} 的单元测试
 *
 * <p>覆盖必填字段、单仓/多仓配置、目标/基线分支，以及项目引用（启用状态、子目录冲突）校验。
 */
@ExtendWith(MockitoExtension.class)
class TaskDocumentValidationTest {

    @Mock
    private AgentProjectMapper projectMapper;

    private TaskDocumentValidator validator;
    private TaskDocumentParser parser;

    @BeforeEach
    void setUp() {
        AgentProjectRefValidator refValidator = new AgentProjectRefValidator();
        ReflectionTestUtils.setField(refValidator, "projectMapper", projectMapper);
        validator = new TaskDocumentValidator();
        ReflectionTestUtils.setField(validator, "projectRefValidator", refValidator);
        parser = new TaskDocumentParser();
    }

    @Test
    void validate_singleRepoDocument_doesNotThrow() {
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                title: "会员充值优惠券抵扣全栈支持"
                targetBranch: "feature/TASK-20261001-088"
                timeoutMinutes: 30
                repoUrl: "git@github.com:Yvesjava/TaskForge.git"
                baseBranch: "main"
                ---
                """;

        assertThatCode(() -> validator.validate(parser.parse(markdown))).doesNotThrowAnyException();
    }

    @Test
    void validate_multiRepoDocument_doesNotThrow() {
        when(projectMapper.selectByProjectCode("backend-service")).thenReturn(enabledProject(1L, "backend-service"));
        when(projectMapper.selectByProjectCode("frontend-portal")).thenReturn(enabledProject(2L, "frontend-portal"));
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                title: "会员充值优惠券抵扣全栈支持"
                targetBranch: "feature/TASK-20261001-088"
                timeoutMinutes: 45
                projects:
                  - code: "backend-service"
                    baseBranch: "main"
                    subDir: "backend"
                  - code: "frontend-portal"
                    baseBranch: "main"
                    subDir: "frontend"
                ---
                """;

        assertThatCode(() -> validator.validate(parser.parse(markdown))).doesNotThrowAnyException();
    }

    @Test
    void validate_missingTitle_throws() {
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                targetBranch: "feature/TASK-20261001-088"
                timeoutMinutes: 30
                repoUrl: "git@github.com:Yvesjava/TaskForge.git"
                baseBranch: "main"
                ---
                """;

        assertThatThrownBy(() -> validator.validate(parser.parse(markdown)))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    assertThat(((ServiceException) ex).getCode())
                            .isEqualTo(ErrorCodeConstants.DOCUMENT_FIELD_REQUIRED.getCode());
                    assertThat(ex.getMessage()).contains("title");
                });
    }

    @Test
    void validate_missingTimeoutMinutes_throws() {
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                title: "会员充值优惠券抵扣全栈支持"
                targetBranch: "feature/TASK-20261001-088"
                repoUrl: "git@github.com:Yvesjava/TaskForge.git"
                baseBranch: "main"
                ---
                """;

        assertThatThrownBy(() -> validator.validate(parser.parse(markdown)))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    assertThat(((ServiceException) ex).getCode())
                            .isEqualTo(ErrorCodeConstants.DOCUMENT_FIELD_REQUIRED.getCode());
                    assertThat(ex.getMessage()).contains("timeoutMinutes");
                });
    }

    @Test
    void validate_missingRepoConfig_throws() {
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                title: "会员充值优惠券抵扣全栈支持"
                targetBranch: "feature/TASK-20261001-088"
                timeoutMinutes: 30
                ---
                """;

        assertThatThrownBy(() -> validator.validate(parser.parse(markdown)))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.DOCUMENT_REPO_MODE_MISSING.getCode()));
    }

    @Test
    void validate_conflictingRepoConfig_throws() {
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                title: "会员充值优惠券抵扣全栈支持"
                targetBranch: "feature/TASK-20261001-088"
                timeoutMinutes: 30
                repoUrl: "git@github.com:Yvesjava/TaskForge.git"
                baseBranch: "main"
                projects:
                  - code: "backend-service"
                    baseBranch: "main"
                    subDir: "backend"
                ---
                """;

        assertThatThrownBy(() -> validator.validate(parser.parse(markdown)))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.DOCUMENT_REPO_MODE_CONFLICT.getCode()));
    }

    @Test
    void validate_invalidTargetBranch_throws() {
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                title: "会员充值优惠券抵扣全栈支持"
                targetBranch: "feature/bad branch"
                timeoutMinutes: 30
                repoUrl: "git@github.com:Yvesjava/TaskForge.git"
                baseBranch: "main"
                ---
                """;

        assertThatThrownBy(() -> validator.validate(parser.parse(markdown)))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.DOCUMENT_TARGET_BRANCH_INVALID.getCode()));
    }

    @Test
    void validate_singleRepoMissingBaseBranch_throws() {
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                title: "会员充值优惠券抵扣全栈支持"
                targetBranch: "feature/TASK-20261001-088"
                timeoutMinutes: 30
                repoUrl: "git@github.com:Yvesjava/TaskForge.git"
                ---
                """;

        assertThatThrownBy(() -> validator.validate(parser.parse(markdown)))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    assertThat(((ServiceException) ex).getCode())
                            .isEqualTo(ErrorCodeConstants.DOCUMENT_FIELD_REQUIRED.getCode());
                    assertThat(ex.getMessage()).contains("baseBranch");
                });
    }

    @Test
    void validate_singleRepoInvalidBaseBranch_throws() {
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                title: "会员充值优惠券抵扣全栈支持"
                targetBranch: "feature/TASK-20261001-088"
                timeoutMinutes: 30
                repoUrl: "git@github.com:Yvesjava/TaskForge.git"
                baseBranch: "bad branch"
                ---
                """;

        assertThatThrownBy(() -> validator.validate(parser.parse(markdown)))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.DOCUMENT_BASE_BRANCH_INVALID.getCode()));
    }

    @Test
    void validate_disabledProject_throws() {
        when(projectMapper.selectByProjectCode("backend-service"))
                .thenReturn(disabledProject(1L, "backend-service"));
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                title: "会员充值优惠券抵扣全栈支持"
                targetBranch: "feature/TASK-20261001-088"
                timeoutMinutes: 30
                projects:
                  - code: "backend-service"
                    baseBranch: "main"
                    subDir: "backend"
                ---
                """;

        assertThatThrownBy(() -> validator.validate(parser.parse(markdown)))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_REF_DISABLED.getCode()));
    }

    @Test
    void validate_duplicateSubDir_throws() {
        when(projectMapper.selectByProjectCode("backend-service")).thenReturn(enabledProject(1L, "backend-service"));
        when(projectMapper.selectByProjectCode("frontend-portal")).thenReturn(enabledProject(2L, "frontend-portal"));
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                title: "会员充值优惠券抵扣全栈支持"
                targetBranch: "feature/TASK-20261001-088"
                timeoutMinutes: 30
                projects:
                  - code: "backend-service"
                    baseBranch: "main"
                    subDir: "backend"
                  - code: "frontend-portal"
                    baseBranch: "main"
                    subDir: "backend"
                ---
                """;

        assertThatThrownBy(() -> validator.validate(parser.parse(markdown)))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_REF_SUB_DIR_CONFLICT.getCode()));
    }

    @Test
    void validate_missingProjectSubDir_throws() {
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                title: "会员充值优惠券抵扣全栈支持"
                targetBranch: "feature/TASK-20261001-088"
                timeoutMinutes: 30
                projects:
                  - code: "backend-service"
                    baseBranch: "main"
                ---
                """;

        assertThatThrownBy(() -> validator.validate(parser.parse(markdown)))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    assertThat(((ServiceException) ex).getCode())
                            .isEqualTo(ErrorCodeConstants.DOCUMENT_FIELD_REQUIRED.getCode());
                    assertThat(ex.getMessage()).contains("projects[0].subDir");
                });
    }

    private static AgentProjectDO enabledProject(Long id, String projectCode) {
        return AgentProjectDO.builder()
                .id(id)
                .projectCode(projectCode)
                .status(CommonStatusEnum.ENABLE.getStatus())
                .build();
    }

    private static AgentProjectDO disabledProject(Long id, String projectCode) {
        return AgentProjectDO.builder()
                .id(id)
                .projectCode(projectCode)
                .status(CommonStatusEnum.DISABLE.getStatus())
                .build();
    }

}
