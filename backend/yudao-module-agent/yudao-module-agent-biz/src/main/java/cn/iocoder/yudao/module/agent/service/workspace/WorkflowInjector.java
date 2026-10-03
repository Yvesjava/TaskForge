package cn.iocoder.yudao.module.agent.service.workspace;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_WRITE_TASK_DOC_FAILED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_WRITE_WORKFLOW_FAILED;

/**
 * 任务文档与系统级执行约束注入器
 *
 * <p>负责在聚合工作区根目录下生成 {@code .ai/task.md} 与 {@code .ai/WORKFLOW.md}。
 * {@code task.md} 保存原始任务文档，{@code WORKFLOW.md} 固化 Symphony 风格的
 * 非交互执行协议，约束 Codex 仅按任务文档的范围、计划与验收步骤工作，
 * 并在所有验收命令通过前禁止提交或推送。
 *
 * @author TaskForge
 */
@Component
public class WorkflowInjector {

    /**
     * 注入文件所在目录名
     */
    public static final String AI_DIRECTORY = ".ai";

    /**
     * 原始任务文档文件名
     */
    public static final String TASK_FILE_NAME = "task.md";

    /**
     * 系统级执行约束文件名
     */
    public static final String WORKFLOW_FILE_NAME = "WORKFLOW.md";

    /**
     * 固化的系统级执行协议。
     *
     * <p>内容与《技术开发与架构设计文档》第 4 节保持一致，禁止在该模板中
     * 引入任何凭证或环境相关字段。
     */
    private static final String WORKFLOW_TEMPLATE = """
            # Role and Execution Protocol for Coding Agent

            You are Symphony-Agent, an automated developer running within an isolated workspace.
            Your mission is to read and fulfill the execution requirements specified in `.ai/task.md`.

            ## Execution Rules (STRICT):
            1. **Scope Restriction**: Modify ONLY files within the subprojects listed under `projects` in `.ai/task.md`. Never make any changes outside these directory boundaries.
            2. **Phase 1: Implementation**:
               - Read the Execution Plan carefully.
               - For multi-project tasks (e.g. backend and frontend), ensure that API endpoints, DTO models, and parameter names match precisely between frontend client calls and backend controllers.
            3. **Phase 2: Verification (Non-Negotiable)**:
               - Once code changes are made, run the exact bash commands defined in `Verification Steps` of `.ai/task.md`.
               - If any test or build fails, read the terminal output, reason through the root cause, and correct your implementation.
               - You are permitted up to 2 self-correction iterations.
            4. **Phase 3: Workpad Summary**:
               - Generate a concise summary of changes and validation test results into `.ai/workpad_summary.json` following this format:
                 ```json
                 {
                   "allPassed": true,
                   "testsExecuted": ["mvn test -Dtest=OrderTest", "pnpm test"],
                   "modifiedFiles": ["backend/src/...", "frontend/src/..."],
                   "notes": "Added coupon deduction validation and UI bindings."
                 }
                 ```
            5. **Commit Gate**: Do not run `git commit` or `git push` until every verification command exits with code `0`; never use `git push --force`.
            6. **No Terminal Prompts**: Run commands in non-interactive batch mode. Do NOT pause to wait for human confirmation.
            7. **Result Contract**: If verification fails after the allowed retries, write the failure details to `.ai/workpad_summary.json` and exit non-zero so the controller marks the task `FAILED`.
            """.stripIndent() + "\n";

    /**
     * 将原始任务文档与系统级执行约束写入聚合根目录的 {@code .ai} 目录。
     *
     * <p>该方法按顺序写入 {@code task.md} 与 {@code WORKFLOW.md}；任一文件写入失败
     * 都会抛出异常，由调用方执行回滚，避免启动 Codex 时缺少执行约束。
     *
     * @param root    聚合工作区根目录
     * @param taskDoc 原始任务文档，允许为空字符串
     */
    public void writeArtifacts(Path root, String taskDoc) {
        Path aiDir = root.resolve(AI_DIRECTORY);
        try {
            Files.createDirectories(aiDir);
        } catch (IOException e) {
            throw exception(WORKTREE_WRITE_TASK_DOC_FAILED, reasonOf(e));
        }
        writeFile(aiDir.resolve(TASK_FILE_NAME), taskDoc == null ? "" : taskDoc, WORKTREE_WRITE_TASK_DOC_FAILED);
        writeFile(aiDir.resolve(WORKFLOW_FILE_NAME), workflowContent(), WORKTREE_WRITE_WORKFLOW_FAILED);
    }

    /**
     * 返回固化的 {@code WORKFLOW.md} 内容。
     *
     * @return 系统级执行协议全文
     */
    public String workflowContent() {
        return WORKFLOW_TEMPLATE;
    }

    private void writeFile(Path path, String content, ErrorCode errorCode) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw exception(errorCode, reasonOf(e));
        }
    }

    private String reasonOf(IOException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

}
