package cn.iocoder.yudao.module.agent.service.workspace;

import cn.iocoder.yudao.module.agent.framework.workspace.config.AgentWorkspaceProperties;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WorkflowInjector} 的契约测试
 *
 * <p>验证聚合工作区根目录会同时生成原始任务文档 {@code .ai/task.md}
 * 与系统级执行约束 {@code .ai/WORKFLOW.md}，且协议内容包含范围限制、
 * 验收步骤、提交门禁与失败结果契约等关键约束。
 */
class WorkflowInjectionTest {

    @TempDir
    Path tempDir;

    private final GitCommandRunner gitRunner = new GitCommandRunner();

    @Test
    void injector_writesTaskDocAndWorkflowContract() throws Exception {
        WorkflowInjector injector = new WorkflowInjector();
        Path root = tempDir.resolve("workspace").resolve("dirA-TASK-1");

        injector.writeArtifacts(root, "# Task\n\nbuild backend and frontend");

        assertThat(root.resolve(".ai/task.md")).hasContent("# Task\n\nbuild backend and frontend");
        assertWorkflowContract(root.resolve(".ai/WORKFLOW.md"));
    }

    @Test
    void createCompositeWorkspace_injectsWorkflowAlongsideTaskDoc() throws Exception {
        Path source = tempDir.resolve("source");
        initSourceRepo(source, "pom.xml", "<project/>\n");
        WorktreeManager manager = manager(tempDir.resolve("workspace"), tempDir.resolve("bare"));

        CompositeWorkspace workspace = manager.createCompositeWorkspace(
                "TASK-WORKFLOW-1", "feat/lzc-38", "# Task\n\nbuild backend",
                List.of(WorkspaceProject.builder()
                        .projectCode("backend-service")
                        .gitUrl(source.toString())
                        .baseBranch("main")
                        .subDir("backend")
                        .build()));

        assertThat(workspace.getRoot().resolve(".ai/task.md")).hasContent("# Task\n\nbuild backend");
        assertWorkflowContract(workspace.getRoot().resolve(".ai/WORKFLOW.md"));
    }

    private void assertWorkflowContract(Path workflowPath) throws Exception {
        String workflow = Files.readString(workflowPath);
        assertThat(workflow)
                .contains("Role and Execution Protocol for Coding Agent")
                .contains("read and fulfill the execution requirements specified in `.ai/task.md`")
                .contains("**Scope Restriction**")
                .contains("Verification Steps")
                .contains("**Commit Gate**")
                .contains("**Result Contract**")
                .contains("never use `git push --force`")
                .contains(".ai/workpad_summary.json");
    }

    private WorktreeManager manager(Path workspaceRoot, Path bareRoot) {
        AgentWorkspaceProperties properties = new AgentWorkspaceProperties();
        properties.setBareRepoRoot(bareRoot.toString());
        properties.setWorkspaceRoot(workspaceRoot.toString());
        return new WorktreeManager(properties, new BareRepoManager(properties, gitRunner), gitRunner,
                new WorkflowInjector());
    }

    private void initSourceRepo(Path source, String fileName, String content) throws Exception {
        gitRunner.run(tempDir, List.of("init", "-b", "main", source.toString()));
        gitRunner.run(source, List.of("config", "user.email", "test@example.com"));
        gitRunner.run(source, List.of("config", "user.name", "test"));
        Files.writeString(source.resolve(fileName), content);
        gitRunner.run(source, List.of("add", "."));
        gitRunner.run(source, List.of("commit", "-m", "init"));
    }

}
