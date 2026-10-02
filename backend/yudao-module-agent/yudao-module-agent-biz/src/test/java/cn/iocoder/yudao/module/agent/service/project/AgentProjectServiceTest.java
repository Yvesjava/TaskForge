package cn.iocoder.yudao.module.agent.service.project;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.agent.controller.admin.project.AgentProjectController;
import cn.iocoder.yudao.module.agent.controller.admin.project.vo.project.AgentProjectPageReqVO;
import cn.iocoder.yudao.module.agent.controller.admin.project.vo.project.AgentProjectSaveReqVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentProjectMapper;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentProjectServiceImpl} 的单元测试类
 *
 * 覆盖项目资产 CRUD、项目代号唯一性、缺失记录校验，以及控制层权限注解契约。
 */
@ExtendWith(MockitoExtension.class)
class AgentProjectServiceTest {

    @Mock
    private AgentProjectMapper projectMapper;

    @InjectMocks
    private AgentProjectServiceImpl projectService;

    @Test
    void createProject_success() {
        AgentProjectSaveReqVO reqVO = buildSaveReqVO(null);
        when(projectMapper.selectByProjectCode("backend-service")).thenReturn(null);
        doAnswer(invocation -> {
            AgentProjectDO project = invocation.getArgument(0);
            project.setId(1024L);
            return 1;
        }).when(projectMapper).insert(any(AgentProjectDO.class));

        Long id = projectService.createProject(reqVO);

        assertThat(id).isEqualTo(1024L);
        ArgumentCaptor<AgentProjectDO> captor = ArgumentCaptor.forClass(AgentProjectDO.class);
        verify(projectMapper).insert(captor.capture());
        assertThat(captor.getValue().getProjectCode()).isEqualTo("backend-service");
        assertThat(captor.getValue().getStatus()).isEqualTo(CommonStatusEnum.ENABLE.getStatus());
    }

    @Test
    void createProject_duplicateCode_throws() {
        AgentProjectSaveReqVO reqVO = buildSaveReqVO(null);
        AgentProjectDO existing = AgentProjectDO.builder().id(1L).projectCode("backend-service").build();
        when(projectMapper.selectByProjectCode("backend-service")).thenReturn(existing);

        assertThatThrownBy(() -> projectService.createProject(reqVO))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_CODE_DUPLICATE.getCode()));
        verify(projectMapper, never()).insert(any(AgentProjectDO.class));
    }

    @Test
    void updateProject_success() {
        AgentProjectSaveReqVO reqVO = buildSaveReqVO(1024L);
        AgentProjectDO existing = AgentProjectDO.builder().id(1024L).projectCode("backend-service").build();
        when(projectMapper.selectById(1024L)).thenReturn(existing);
        when(projectMapper.selectByProjectCode("backend-service")).thenReturn(existing);

        projectService.updateProject(reqVO);

        verify(projectMapper).updateById(any(AgentProjectDO.class));
    }

    @Test
    void updateProject_notFound_throws() {
        AgentProjectSaveReqVO reqVO = buildSaveReqVO(1024L);
        when(projectMapper.selectById(1024L)).thenReturn(null);

        assertThatThrownBy(() -> projectService.updateProject(reqVO))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_NOT_FOUND.getCode()));
    }

    @Test
    void updateProject_duplicateCode_throws() {
        AgentProjectSaveReqVO reqVO = buildSaveReqVO(1024L);
        AgentProjectDO existing = AgentProjectDO.builder().id(1024L).projectCode("backend-service").build();
        AgentProjectDO other = AgentProjectDO.builder().id(999L).projectCode("backend-service").build();
        when(projectMapper.selectById(1024L)).thenReturn(existing);
        when(projectMapper.selectByProjectCode("backend-service")).thenReturn(other);

        assertThatThrownBy(() -> projectService.updateProject(reqVO))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_CODE_DUPLICATE.getCode()));
        verify(projectMapper, never()).updateById(any(AgentProjectDO.class));
    }

    @Test
    void updateProjectStatus_success() {
        AgentProjectDO existing = AgentProjectDO.builder().id(1024L).build();
        when(projectMapper.selectById(1024L)).thenReturn(existing);

        projectService.updateProjectStatus(1024L, CommonStatusEnum.DISABLE.getStatus());

        ArgumentCaptor<AgentProjectDO> captor = ArgumentCaptor.forClass(AgentProjectDO.class);
        verify(projectMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1024L);
        assertThat(captor.getValue().getStatus()).isEqualTo(CommonStatusEnum.DISABLE.getStatus());
    }

    @Test
    void updateProjectStatus_notFound_throws() {
        when(projectMapper.selectById(1024L)).thenReturn(null);

        assertThatThrownBy(() -> projectService.updateProjectStatus(1024L, CommonStatusEnum.DISABLE.getStatus()))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_NOT_FOUND.getCode()));
    }

    @Test
    void deleteProject_success() {
        AgentProjectDO existing = AgentProjectDO.builder().id(1024L).build();
        when(projectMapper.selectById(1024L)).thenReturn(existing);

        projectService.deleteProject(1024L);

        verify(projectMapper).deleteById(1024L);
    }

    @Test
    void deleteProject_notFound_throws() {
        when(projectMapper.selectById(1024L)).thenReturn(null);

        assertThatThrownBy(() -> projectService.deleteProject(1024L))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_NOT_FOUND.getCode()));
    }

    @Test
    void getProject_success() {
        AgentProjectDO existing = AgentProjectDO.builder().id(1024L).build();
        when(projectMapper.selectById(1024L)).thenReturn(existing);

        assertThat(projectService.getProject(1024L)).isSameAs(existing);
    }

    @Test
    void getProject_missing_returnsNull() {
        when(projectMapper.selectById(1024L)).thenReturn(null);

        assertThat(projectService.getProject(1024L)).isNull();
    }

    @Test
    void getProjectPage_success() {
        AgentProjectPageReqVO reqVO = new AgentProjectPageReqVO();
        AgentProjectDO existing = AgentProjectDO.builder().id(1024L).build();
        PageResult<AgentProjectDO> pageResult = new PageResult<>(Collections.singletonList(existing), 1L);
        when(projectMapper.selectPage(reqVO)).thenReturn(pageResult);

        assertThat(projectService.getProjectPage(reqVO)).isSameAs(pageResult);
    }

    @Test
    void controllerEndpointsDeclareRequiredPermissions() {
        Map<String, String> expected = Map.of(
                "createProject", "agent:project:create",
                "updateProject", "agent:project:update",
                "updateProjectStatus", "agent:project:update",
                "deleteProject", "agent:project:delete",
                "getProject", "agent:project:query",
                "getProjectPage", "agent:project:query");

        expected.forEach((methodName, permission) -> {
            Method method = findMethod(methodName);
            assertThat(method).as("missing controller method %s", methodName).isNotNull();
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            assertThat(annotation).as("method %s missing @PreAuthorize", methodName).isNotNull();
            assertThat(annotation.value()).as("method %s wrong permission", methodName)
                    .isEqualTo("@ss.hasPermission('" + permission + "')");
        });
    }

    private static Method findMethod(String methodName) {
        return Arrays.stream(AgentProjectController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElse(null);
    }

    private static AgentProjectSaveReqVO buildSaveReqVO(Long id) {
        AgentProjectSaveReqVO reqVO = new AgentProjectSaveReqVO();
        reqVO.setId(id);
        reqVO.setProjectCode("backend-service");
        reqVO.setName("后端服务");
        reqVO.setGitUrl("git@github.com:Yvesjava/TaskForge.git");
        reqVO.setDefaultBranch("main");
        reqVO.setBuildTool("MAVEN");
        reqVO.setTestCommand("mvn clean test");
        reqVO.setCredentialRef("taskforge-git");
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return reqVO;
    }

}
