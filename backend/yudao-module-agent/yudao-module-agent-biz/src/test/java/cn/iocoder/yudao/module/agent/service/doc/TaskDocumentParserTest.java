package cn.iocoder.yudao.module.agent.service.doc;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TaskDocumentParser} 的单元测试
 *
 * <p>覆盖单仓与多仓示例解析、字段类型转换，以及解析错误中的字段位置信息。
 */
class TaskDocumentParserTest {

    private TaskDocumentParser parser;

    @BeforeEach
    void setUp() {
        parser = new TaskDocumentParser();
    }

    @Test
    void parse_singleRepoDocument_returnsTypedFrontMatter() {
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                title: "会员充值优惠券抵扣全栈支持"
                targetBranch: "feature/TASK-20261001-088"
                timeoutMinutes: 30
                repoUrl: "git@github.com:Yvesjava/TaskForge.git"
                baseBranch: "main"
                ---

                # 需求目标与上下文
                正文内容
                """;

        TaskDocument document = parser.parse(markdown);

        TaskFrontMatter frontMatter = document.getFrontMatter();
        assertThat(frontMatter.getTaskId()).isEqualTo("TASK-20261001-088");
        assertThat(frontMatter.getTitle()).isEqualTo("会员充值优惠券抵扣全栈支持");
        assertThat(frontMatter.getTargetBranch()).isEqualTo("feature/TASK-20261001-088");
        assertThat(frontMatter.getTimeoutMinutes()).isEqualTo(30);
        assertThat(frontMatter.getRepoUrl()).isEqualTo("git@github.com:Yvesjava/TaskForge.git");
        assertThat(frontMatter.getBaseBranch()).isEqualTo("main");
        assertThat(frontMatter.getProjects()).isNull();
        assertThat(document.getBody()).contains("# 需求目标与上下文", "正文内容");
    }

    @Test
    void parse_multiRepoDocument_returnsTypedFrontMatter() {
        String markdown = """
                ---
                taskId: "TASK-20261001-088"
                title: "会员充值优惠券抵扣全栈支持"
                targetBranch: "feature/TASK-20261001-088"
                timeoutMinutes: 45
                priority: 10
                dependsOnTaskId: 9012
                projects:
                  - code: "backend-service"
                    baseBranch: "main"
                    subDir: "backend"
                  - code: "frontend-portal"
                    baseBranch: "main"
                    subDir: "frontend"
                ---

                # 需求目标与上下文
                """;

        TaskDocument document = parser.parse(markdown);

        TaskFrontMatter frontMatter = document.getFrontMatter();
        assertThat(frontMatter.getTimeoutMinutes()).isEqualTo(45);
        assertThat(frontMatter.getPriority()).isEqualTo(10);
        assertThat(frontMatter.getDependsOnTaskId()).isEqualTo(9012L);
        assertThat(frontMatter.getRepoUrl()).isNull();
        assertThat(frontMatter.getBaseBranch()).isNull();

        List<TaskProjectRef> projects = frontMatter.getProjects();
        assertThat(projects).hasSize(2);
        assertThat(projects.get(0).getCode()).isEqualTo("backend-service");
        assertThat(projects.get(0).getBaseBranch()).isEqualTo("main");
        assertThat(projects.get(0).getSubDir()).isEqualTo("backend");
        assertThat(projects.get(1).getCode()).isEqualTo("frontend-portal");
        assertThat(projects.get(1).getBaseBranch()).isEqualTo("main");
        assertThat(projects.get(1).getSubDir()).isEqualTo("frontend");
    }

    @Test
    void parse_plainUnquotedScalars_returnsStringFields() {
        String markdown = """
                ---
                taskId: TASK-20261001-088
                title: 会员充值
                targetBranch: feature/TASK-20261001-088
                timeoutMinutes: 45
                repoUrl: git@github.com:Yvesjava/TaskForge.git
                baseBranch: main
                ---
                """;

        TaskFrontMatter frontMatter = parser.parse(markdown).getFrontMatter();

        assertThat(frontMatter.getTaskId()).isEqualTo("TASK-20261001-088");
        assertThat(frontMatter.getTitle()).isEqualTo("会员充值");
        assertThat(frontMatter.getTargetBranch()).isEqualTo("feature/TASK-20261001-088");
        assertThat(frontMatter.getTimeoutMinutes()).isEqualTo(45);
        assertThat(frontMatter.getRepoUrl()).isEqualTo("git@github.com:Yvesjava/TaskForge.git");
        assertThat(frontMatter.getBaseBranch()).isEqualTo("main");
    }

    @Test
    void parse_missingFrontMatter_throws() {
        assertThatThrownBy(() -> parser.parse("只有正文，没有 Front Matter"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.DOCUMENT_FRONT_MATTER_NOT_FOUND.getCode()));
    }

    @Test
    void parse_unclosedFrontMatter_throws() {
        assertThatThrownBy(() -> parser.parse("---\ntaskId: \"TASK-1\"\n"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.DOCUMENT_FRONT_MATTER_NOT_CLOSED.getCode()));
    }

    @Test
    void parse_emptyFrontMatter_throws() {
        assertThatThrownBy(() -> parser.parse("---\n---\n"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.DOCUMENT_FRONT_MATTER_EMPTY.getCode()));
    }

    @Test
    void parse_topLevelNotMapping_throws() {
        assertThatThrownBy(() -> parser.parse("---\n- a\n- b\n---\n"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.DOCUMENT_FRONT_MATTER_NOT_MAPPING.getCode()));
    }

    @Test
    void parse_wrongTypeTimeoutMinutes_throwsWithFieldLocation() {
        String markdown = """
                ---
                taskId: "TASK-1"
                title: "t"
                targetBranch: "feature/x"
                timeoutMinutes: "45"
                ---
                """;

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException serviceException = (ServiceException) ex;
                    assertThat(serviceException.getCode())
                            .isEqualTo(ErrorCodeConstants.DOCUMENT_FIELD_TYPE_ERROR.getCode());
                    assertThat(serviceException.getMessage())
                            .contains("timeoutMinutes", "第 5 行", "列", "应为整数");
                });
    }

    @Test
    void parse_wrongTypeInProject_throwsWithFieldLocation() {
        String markdown = """
                ---
                taskId: "TASK-1"
                title: "t"
                targetBranch: "feature/x"
                timeoutMinutes: 45
                projects:
                  - code: "backend-service"
                    baseBranch: 123
                    subDir: "backend"
                ---
                """;

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException serviceException = (ServiceException) ex;
                    assertThat(serviceException.getCode())
                            .isEqualTo(ErrorCodeConstants.DOCUMENT_FIELD_TYPE_ERROR.getCode());
                    assertThat(serviceException.getMessage())
                            .contains("projects[0].baseBranch", "第 8 行", "列", "应为字符串");
                });
    }

    @Test
    void parse_invalidYamlSyntax_throwsWithLocation() {
        String markdown = """
                ---
                taskId: "TASK-1"
                title: [未闭合
                ---
                """;

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException serviceException = (ServiceException) ex;
                    assertThat(serviceException.getCode())
                            .isEqualTo(ErrorCodeConstants.DOCUMENT_FRONT_MATTER_INVALID.getCode());
                    assertThat(serviceException.getMessage()).contains("第", "行");
                });
    }

    @Test
    void parse_nullOptionalFields_setNull() {
        String markdown = """
                ---
                taskId: "TASK-1"
                title: "t"
                targetBranch: "feature/x"
                timeoutMinutes: 45
                priority: null
                dependsOnTaskId: null
                ---
                """;

        TaskFrontMatter frontMatter = parser.parse(markdown).getFrontMatter();

        assertThat(frontMatter.getPriority()).isNull();
        assertThat(frontMatter.getDependsOnTaskId()).isNull();
    }

    @Test
    void parse_nullOrBlankDocument_throwsDocumentEmpty() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.DOCUMENT_EMPTY.getCode()));
        assertThatThrownBy(() -> parser.parse("   \n  "))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.DOCUMENT_EMPTY.getCode()));
    }

    @Test
    void parse_leadingBom_isStrippedBeforeParsing() {
        String markdown = "\uFEFF---\n"
                + "taskId: \"TASK-1\"\n"
                + "title: \"带 BOM 的文档\"\n"
                + "targetBranch: \"feature/x\"\n"
                + "timeoutMinutes: 30\n"
                + "repoUrl: \"git@github.com:Yvesjava/TaskForge.git\"\n"
                + "baseBranch: \"main\"\n"
                + "---\n";

        TaskFrontMatter frontMatter = parser.parse(markdown).getFrontMatter();

        assertThat(frontMatter.getTaskId()).isEqualTo("TASK-1");
        assertThat(frontMatter.getTitle()).isEqualTo("带 BOM 的文档");
    }

    @Test
    void parse_underscoreInIntLiteral_isConvertedToInteger() {
        String markdown = """
                ---
                taskId: "TASK-1"
                title: "t"
                targetBranch: "feature/x"
                timeoutMinutes: 1_000
                repoUrl: "git@github.com:Yvesjava/TaskForge.git"
                baseBranch: "main"
                ---
                """;

        TaskFrontMatter frontMatter = parser.parse(markdown).getFrontMatter();

        assertThat(frontMatter.getTimeoutMinutes()).isEqualTo(1000);
    }

    @Test
    void parse_projectsNotSequence_throwsTypeError() {
        String markdown = """
                ---
                taskId: "TASK-1"
                title: "t"
                targetBranch: "feature/x"
                timeoutMinutes: 45
                projects: "backend-service"
                ---
                """;

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException serviceException = (ServiceException) ex;
                    assertThat(serviceException.getCode())
                            .isEqualTo(ErrorCodeConstants.DOCUMENT_FIELD_TYPE_ERROR.getCode());
                    assertThat(serviceException.getMessage()).contains("projects", "应为项目列表");
                });
    }

    @Test
    void parse_projectItemNotMapping_throwsTypeError() {
        String markdown = """
                ---
                taskId: "TASK-1"
                title: "t"
                targetBranch: "feature/x"
                timeoutMinutes: 45
                projects:
                  - 123
                ---
                """;

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException serviceException = (ServiceException) ex;
                    assertThat(serviceException.getCode())
                            .isEqualTo(ErrorCodeConstants.DOCUMENT_FIELD_TYPE_ERROR.getCode());
                    assertThat(serviceException.getMessage()).contains("projects[0]", "应为键值对映射");
                });
    }

    @Test
    void parse_wrongTypeDependsOnTaskId_throwsWithFieldLocation() {
        String markdown = """
                ---
                taskId: "TASK-1"
                title: "t"
                targetBranch: "feature/x"
                timeoutMinutes: 45
                dependsOnTaskId: "not-a-number"
                ---
                """;

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException serviceException = (ServiceException) ex;
                    assertThat(serviceException.getCode())
                            .isEqualTo(ErrorCodeConstants.DOCUMENT_FIELD_TYPE_ERROR.getCode());
                    assertThat(serviceException.getMessage()).contains("dependsOnTaskId", "应为整数");
                });
    }

}
