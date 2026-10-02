package cn.iocoder.yudao.module.agent.controller.admin.project.vo.project;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentProjectSaveReqVO} 的字段级配置校验测试
 *
 * 覆盖 Git 地址、默认分支、构建工具、测试命令与凭证引用的非法配置拒绝，
 * 并断言错误可定位到具体字段。
 */
class AgentProjectValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validConfig_hasNoViolations() {
        assertThat(violationPaths(buildValidReqVO())).isEmpty();
    }

    @Test
    void invalidGitUrl_rejectedWithGitUrlPath() {
        AgentProjectSaveReqVO reqVO = buildValidReqVO();
        reqVO.setGitUrl("not-a-valid-git-url");

        assertThat(violationPaths(reqVO)).containsExactlyInAnyOrder("gitUrl");
    }

    @Test
    void invalidDefaultBranch_rejectedWithDefaultBranchPath() {
        AgentProjectSaveReqVO reqVO = buildValidReqVO();
        reqVO.setDefaultBranch("feat..broken");

        assertThat(violationPaths(reqVO)).containsExactlyInAnyOrder("defaultBranch");
    }

    @Test
    void invalidBuildTool_rejectedWithBuildToolPath() {
        AgentProjectSaveReqVO reqVO = buildValidReqVO();
        reqVO.setBuildTool("NPM");

        assertThat(violationPaths(reqVO)).containsExactlyInAnyOrder("buildTool");
    }

    @Test
    void invalidTestCommand_rejectedWithTestCommandPath() {
        AgentProjectSaveReqVO reqVO = buildValidReqVO();
        reqVO.setTestCommand("   ");

        assertThat(violationPaths(reqVO)).containsExactlyInAnyOrder("testCommand");
    }

    @Test
    void invalidCredentialRef_rejectedWithCredentialRefPath() {
        AgentProjectSaveReqVO reqVO = buildValidReqVO();
        reqVO.setCredentialRef("bad ref");

        assertThat(violationPaths(reqVO)).containsExactlyInAnyOrder("credentialRef");
    }

    @Test
    void multipleInvalidFields_reportEachField() {
        AgentProjectSaveReqVO reqVO = buildValidReqVO();
        reqVO.setGitUrl("invalid");
        reqVO.setBuildTool("gradle");
        reqVO.setDefaultBranch("-bad");

        assertThat(violationPaths(reqVO)).contains("gitUrl", "buildTool", "defaultBranch");
    }

    private Set<String> violationPaths(AgentProjectSaveReqVO reqVO) {
        return validator.validate(reqVO).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static AgentProjectSaveReqVO buildValidReqVO() {
        AgentProjectSaveReqVO reqVO = new AgentProjectSaveReqVO();
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
