package cn.iocoder.yudao.module.agent.service.security;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentProjectMapper;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.agent.framework.exec.CommandGate;
import cn.iocoder.yudao.module.agent.framework.exec.CommandNotAllowedException;
import cn.iocoder.yudao.module.agent.framework.exec.CommandSpec;
import cn.iocoder.yudao.module.agent.service.project.AgentProjectRef;
import cn.iocoder.yudao.module.agent.service.project.AgentProjectRefValidator;
import cn.iocoder.yudao.module.agent.service.workspace.WorkspaceProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-OPS-02 路径/命令/仓库/权限安全策略测试
 *
 * <p>验证统一安全策略能够拒绝非法路径（穿越、绝对路径、反斜杠、控制字符、符号链接）、
 * 白名单外命令，以及不存在/停用/跨租户不可见的项目。</p>
 */
@ExtendWith(MockitoExtension.class)
class SecurityPolicyTest {

    @Mock
    private CommandGate commandGate;

    @Mock
    private AgentProjectRefValidator projectRefValidator;

    @Mock
    private AgentProjectMapper projectMapper;

    @InjectMocks
    private SecurityPolicy securityPolicy;

    @TempDir
    Path tempDir;

    @Test
    void checkExecution_rejectsIllegalTaskNo() {
        assertThatThrownBy(() -> securityPolicy.checkExecution(
                "../escape", "feature/x", List.of(), List.of()))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.SECURITY_TASK_NO_INVALID.getCode()));
    }

    @Test
    void checkExecution_rejectsIllegalBranch() {
        assertThatThrownBy(() -> securityPolicy.checkExecution(
                "TASK-1", "feature/bad branch", List.of(), List.of()))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.SECURITY_BRANCH_INVALID.getCode()));
    }

    @Test
    void checkExecution_rejectsIllegalProjectCode() {
        WorkspaceProject project = WorkspaceProject.builder()
                .projectCode("../evil")
                .gitUrl("git@example.com/demo.git")
                .baseBranch("main")
                .subDir("backend")
                .build();

        assertThatThrownBy(() -> securityPolicy.checkExecution(
                "TASK-1", "feature/x", List.of(project), List.of()))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.SECURITY_PROJECT_CODE_INVALID.getCode()));
    }

    @Test
    void checkExecution_rejectsTraversalSubDir() {
        WorkspaceProject project = project("backend-service", "../escape");

        assertThatThrownBy(() -> securityPolicy.checkExecution(
                "TASK-1", "feature/x", List.of(project), List.of()))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.SECURITY_SUB_DIR_INVALID.getCode()));
    }

    @Test
    void checkExecution_rejectsAbsoluteSubDir() {
        WorkspaceProject project = project("backend-service", "/absolute");

        assertThatThrownBy(() -> securityPolicy.checkExecution(
                "TASK-1", "feature/x", List.of(project), List.of()))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.SECURITY_SUB_DIR_INVALID.getCode()));
    }

    @Test
    void checkExecution_rejectsBackslashAndControlChars() {
        assertThatThrownBy(() -> PathSecurityPolicy.requireSafeSubDir("..\\escape"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.SECURITY_SUB_DIR_INVALID.getCode()));
        assertThatThrownBy(() -> PathSecurityPolicy.requireSafeSubDir("evil\0dir"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.SECURITY_SUB_DIR_INVALID.getCode()));
    }

    @Test
    void checkExecution_rejectsNonWhitelistedCommand() {
        CommandSpec command = new CommandSpec("sh", List.of("-c", "rm -rf /"), null);
        doThrow(new CommandNotAllowedException("sh")).when(commandGate).validate(any(CommandSpec.class));

        assertThatThrownBy(() -> securityPolicy.checkExecution(
                "TASK-1", "feature/x", List.of(), List.of(command)))
                .isInstanceOf(CommandNotAllowedException.class);
    }

    @Test
    void checkExecution_acceptsValidPathAndCommands() {
        WorkspaceProject project = project("backend-service", "backend");
        CommandSpec command = new CommandSpec("mvn", List.of("test"), null);

        assertThatCode(() -> securityPolicy.checkExecution(
                "TASK-1", "feature/x", List.of(project), List.of(command)))
                .doesNotThrowAnyException();
        verify(commandGate).validate(command);
    }

    @Test
    void checkProjectRefs_rejectsProjectNotVisibleToCurrentTenant() {
        SecurityPolicy policy = policyWithRealProjectValidator();
        when(projectMapper.selectByProjectCode("other-tenant-project")).thenReturn(null);

        assertThatThrownBy(() -> policy.checkProjectRefs(
                List.of(ref("other-tenant-project", "main", "backend"))))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_REF_NOT_FOUND.getCode()));
    }

    @Test
    void checkProjectRefs_rejectsDisabledProject() {
        SecurityPolicy policy = policyWithRealProjectValidator();
        AgentProjectDO disabled = AgentProjectDO.builder()
                .id(1L)
                .projectCode("disabled-project")
                .status(CommonStatusEnum.DISABLE.getStatus())
                .build();
        when(projectMapper.selectByProjectCode("disabled-project")).thenReturn(disabled);

        assertThatThrownBy(() -> policy.checkProjectRefs(
                List.of(ref("disabled-project", "main", "backend"))))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.PROJECT_REF_DISABLED.getCode()));
    }

    @Test
    void checkProjectRefs_acceptsEnabledVisibleProject() {
        SecurityPolicy policy = policyWithRealProjectValidator();
        when(projectMapper.selectByProjectCode("backend-service"))
                .thenReturn(AgentProjectDO.builder()
                        .id(1L)
                        .projectCode("backend-service")
                        .status(CommonStatusEnum.ENABLE.getStatus())
                        .build());

        assertThatCode(() -> policy.checkProjectRefs(
                List.of(ref("backend-service", "main", "backend"))))
                .doesNotThrowAnyException();
    }

    @Test
    void resolveSubDir_rejectsSymlinkEscape() throws Exception {
        assumeTrue(symlinksSupported(), "符号链接在当前环境不可用");
        Path root = Files.createDirectories(tempDir.resolve("root"));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Files.createSymbolicLink(root.resolve("evil"), outside);

        assertThatThrownBy(() -> PathSecurityPolicy.resolveSubDir(root, "evil"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.SECURITY_SYMLINK_NOT_ALLOWED.getCode()));
    }

    private SecurityPolicy policyWithRealProjectValidator() {
        AgentProjectRefValidator realValidator = new AgentProjectRefValidator();
        ReflectionTestUtils.setField(realValidator, "projectMapper", projectMapper);
        return new SecurityPolicy(commandGate, realValidator);
    }

    private static WorkspaceProject project(String projectCode, String subDir) {
        return WorkspaceProject.builder()
                .projectCode(projectCode)
                .gitUrl("git@example.com/demo.git")
                .baseBranch("main")
                .subDir(subDir)
                .build();
    }

    private static AgentProjectRef ref(String projectCode, String baseBranch, String subDir) {
        return AgentProjectRef.builder()
                .projectCode(projectCode)
                .baseBranch(baseBranch)
                .subDir(subDir)
                .build();
    }

    private boolean symlinksSupported() {
        try {
            Path target = Files.createFile(tempDir.resolve("symlink-target"));
            Path link = tempDir.resolve("symlink-link");
            Files.createSymbolicLink(link, target);
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
