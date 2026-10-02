package cn.iocoder.yudao.module.agent.service.project;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentProjectMapper;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentProjectRefValidator} 的单元测试类
 *
 * 覆盖任务引用项目时的存在性、启用状态、基线分支与子目录冲突校验。
 */
@ExtendWith(MockitoExtension.class)
class AgentProjectRefValidationTest {

    @Mock
    private AgentProjectMapper projectMapper;

    @InjectMocks
    private AgentProjectRefValidator validator;

    @Test
    void validate_validRefs_doesNotThrow() {
        when(projectMapper.selectByProjectCode("backend-service")).thenReturn(enabledProject(1L, "backend-service"));
        when(projectMapper.selectByProjectCode("frontend-portal")).thenReturn(enabledProject(2L, "frontend-portal"));

        assertThatCode(() -> validator.validate(List.of(
                ref("backend-service", "main", "backend"),
                ref("frontend-portal", "main", "frontend"))))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_emptyRefs_doesNotThrow() {
        assertThatCode(() -> validator.validate(Collections.emptyList())).doesNotThrowAnyException();
        verify(projectMapper, never()).selectByProjectCode(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validate_missingProject_throws() {
        when(projectMapper.selectByProjectCode("missing-service")).thenReturn(null);

        assertThatThrownBy(() -> validator.validate(List.of(ref("missing-service", "main", "backend"))))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_REF_NOT_FOUND.getCode()));
    }

    @Test
    void validate_disabledProject_throws() {
        AgentProjectDO project = enabledProject(1L, "backend-service");
        project.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(projectMapper.selectByProjectCode("backend-service")).thenReturn(project);

        assertThatThrownBy(() -> validator.validate(List.of(ref("backend-service", "main", "backend"))))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_REF_DISABLED.getCode()));
    }

    @Test
    void validate_blankBaseBranch_throws() {
        when(projectMapper.selectByProjectCode("backend-service")).thenReturn(enabledProject(1L, "backend-service"));

        assertThatThrownBy(() -> validator.validate(List.of(ref("backend-service", "   ", "backend"))))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_REF_BASE_BRANCH_INVALID.getCode()));
    }

    @Test
    void validate_invalidBaseBranch_throws() {
        when(projectMapper.selectByProjectCode("backend-service")).thenReturn(enabledProject(1L, "backend-service"));

        assertThatThrownBy(() -> validator.validate(List.of(ref("backend-service", "feature/bad branch", "backend"))))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_REF_BASE_BRANCH_INVALID.getCode()));
    }

    @Test
    void validate_invalidSubDir_throws() {
        when(projectMapper.selectByProjectCode("backend-service")).thenReturn(enabledProject(1L, "backend-service"));

        assertThatThrownBy(() -> validator.validate(List.of(ref("backend-service", "main", "../escape"))))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_REF_SUB_DIR_INVALID.getCode()));
    }

    @Test
    void validate_duplicateSubDir_throws() {
        when(projectMapper.selectByProjectCode("backend-service")).thenReturn(enabledProject(1L, "backend-service"));
        when(projectMapper.selectByProjectCode("frontend-portal")).thenReturn(enabledProject(2L, "frontend-portal"));

        assertThatThrownBy(() -> validator.validate(List.of(
                ref("backend-service", "main", "backend"),
                ref("frontend-portal", "main", "backend"))))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_REF_SUB_DIR_CONFLICT.getCode()));
    }

    private static AgentProjectDO enabledProject(Long id, String projectCode) {
        return AgentProjectDO.builder()
                .id(id)
                .projectCode(projectCode)
                .status(CommonStatusEnum.ENABLE.getStatus())
                .build();
    }

    private static AgentProjectRef ref(String projectCode, String baseBranch, String subDir) {
        return AgentProjectRef.builder()
                .projectCode(projectCode)
                .baseBranch(baseBranch)
                .subDir(subDir)
                .build();
    }

}
