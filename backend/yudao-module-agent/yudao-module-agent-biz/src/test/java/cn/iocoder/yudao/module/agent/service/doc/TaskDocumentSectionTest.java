package cn.iocoder.yudao.module.agent.service.doc;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TaskDocumentSectionValidator} 的单元测试
 *
 * <p>覆盖正文必需小节的缺失/为空错误，以及执行计划、验收标准和验收步骤的格式校验。
 */
class TaskDocumentSectionTest {

    private TaskDocumentSectionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TaskDocumentSectionValidator();
    }

    @Test
    void validate_completeDocument_doesNotThrow() {
        TaskDocument document = document("""
                # 需求目标与上下文

                会员充值优惠券抵扣全栈支持。

                ## 任务执行计划

                - [ ] 完成后端接口与测试
                - [ ] 完成前端页面与类型定义

                ## 验收步骤

                1. `cd backend && mvn test`
                2. `cd frontend && pnpm run type-check && pnpm test`

                ## 验收标准

                - [ ] 所有验收命令通过
                - [ ] 不破坏既有功能
                """);

        assertThatCode(() -> validator.validate(document)).doesNotThrowAnyException();
    }

    @Test
    void validate_missingGoal_throwsWithSectionName() {
        TaskDocument document = document("""
                ## 任务执行计划

                - [ ] 完成后端接口

                ## 验收步骤

                1. `cd backend && mvn test`

                ## 验收标准

                - [ ] 所有验收命令通过
                """);

        assertMissingSection(() -> validator.validate(document), "需求目标与上下文");
    }

    @Test
    void validate_emptyGoal_throws() {
        TaskDocument document = document("""
                # 需求目标与上下文

                ## 任务执行计划

                - [ ] 完成后端接口

                ## 验收步骤

                1. `cd backend && mvn test`

                ## 验收标准

                - [ ] 所有验收命令通过
                """);

        assertSectionError(() -> validator.validate(document),
                ErrorCodeConstants.DOCUMENT_SECTION_EMPTY, "需求目标与上下文");
    }

    @Test
    void validate_missingPlan_throwsWithSectionName() {
        TaskDocument document = document("""
                # 需求目标与上下文

                说明

                ## 验收步骤

                1. `cd backend && mvn test`

                ## 验收标准

                - [ ] 所有验收命令通过
                """);

        assertMissingSection(() -> validator.validate(document), "任务执行计划");
    }

    @Test
    void validate_missingSteps_throwsWithSectionName() {
        TaskDocument document = document("""
                # 需求目标与上下文

                说明

                ## 任务执行计划

                - [ ] 完成后端接口

                ## 验收标准

                - [ ] 所有验收命令通过
                """);

        assertMissingSection(() -> validator.validate(document), "验收步骤");
    }

    @Test
    void validate_missingCriteria_throwsWithSectionName() {
        TaskDocument document = document("""
                # 需求目标与上下文

                说明

                ## 任务执行计划

                - [ ] 完成后端接口

                ## 验收步骤

                1. `cd backend && mvn test`
                """);

        assertMissingSection(() -> validator.validate(document), "验收标准");
    }

    @Test
    void validate_emptyPlan_throws() {
        TaskDocument document = document("""
                # 需求目标与上下文

                说明

                ## 任务执行计划

                ## 验收步骤

                1. `cd backend && mvn test`

                ## 验收标准

                - [ ] 所有验收命令通过
                """);

        assertSectionError(() -> validator.validate(document),
                ErrorCodeConstants.DOCUMENT_SECTION_EMPTY, "任务执行计划");
    }

    @Test
    void validate_planWithoutChecklist_throws() {
        TaskDocument document = document("""
                # 需求目标与上下文

                说明

                ## 任务执行计划

                - 完成后端接口

                ## 验收步骤

                1. `cd backend && mvn test`

                ## 验收标准

                - [ ] 所有验收命令通过
                """);

        assertSectionError(() -> validator.validate(document),
                ErrorCodeConstants.DOCUMENT_SECTION_NOT_CHECKLIST, "任务执行计划");
    }

    @Test
    void validate_criteriaWithoutChecklist_throws() {
        TaskDocument document = document("""
                # 需求目标与上下文

                说明

                ## 任务执行计划

                - [ ] 完成后端接口

                ## 验收步骤

                1. `cd backend && mvn test`

                ## 验收标准

                - 所有验收命令通过
                """);

        assertSectionError(() -> validator.validate(document),
                ErrorCodeConstants.DOCUMENT_SECTION_NOT_CHECKLIST, "验收标准");
    }

    @Test
    void validate_stepsWithoutExecutableItem_throws() {
        TaskDocument document = document("""
                # 需求目标与上下文

                说明

                ## 任务执行计划

                - [ ] 完成后端接口

                ## 验收步骤

                1. 确认页面可以正常打开

                ## 验收标准

                - [ ] 所有验收命令通过
                """);

        assertSectionError(() -> validator.validate(document),
                ErrorCodeConstants.DOCUMENT_SECTION_STEP_NOT_EXECUTABLE, "验收步骤");
    }

    @Test
    void validate_nullDocument_throwsDocumentEmpty() {
        assertThatThrownBy(() -> validator.validate((TaskDocument) null))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getCode())
                        .isEqualTo(ErrorCodeConstants.DOCUMENT_EMPTY.getCode()));
    }

    private TaskDocument document(String body) {
        return TaskDocument.builder()
                .frontMatter(null)
                .body(body)
                .build();
    }

    private void assertMissingSection(ThrowingRunnable runnable, String sectionName) {
        assertSectionError(runnable, ErrorCodeConstants.DOCUMENT_SECTION_MISSING, sectionName);
    }

    private void assertSectionError(ThrowingRunnable runnable, ErrorCode code, String sectionName) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException serviceException = (ServiceException) ex;
                    assertThat(serviceException.getCode()).isEqualTo(code.getCode());
                    assertThat(serviceException.getMessage()).contains(sectionName);
                });
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run();
    }

}
