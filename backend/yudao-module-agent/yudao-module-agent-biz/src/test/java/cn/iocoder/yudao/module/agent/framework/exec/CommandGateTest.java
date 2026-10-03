package cn.iocoder.yudao.module.agent.framework.exec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CommandGate} 的契约测试
 *
 * <p>验证构建/测试命令白名单校验、顺序串行执行以及“所有验收命令通过后才允许
 * 提交/推送”的门禁语义；同时用真实 JVM 子进程覆盖非零退出与零退出的执行路径。
 */
class CommandGateTest {

    @TempDir
    Path tempDir;

    @Test
    void isAllowed_acceptsBuildToolsAndRejectsOthers() {
        CommandGate gate = new CommandGate((command, dir) -> success(command), new AgentExecProperties());

        assertThat(gate.isAllowed("mvn")).isTrue();
        assertThat(gate.isAllowed("mvnw")).isTrue();
        assertThat(gate.isAllowed("pnpm")).isTrue();
        assertThat(gate.isAllowed("gradle")).isTrue();
        assertThat(gate.isAllowed("sh")).isFalse();
        assertThat(gate.isAllowed("rm")).isFalse();
        assertThat(gate.isAllowed("git")).isFalse();
    }

    @Test
    void validate_rejectsNonWhitelistedCommand() {
        CommandGate gate = new CommandGate((command, dir) -> success(command), propertiesWith("mvn", "pnpm"));

        assertThatThrownBy(() -> gate.validate(new CommandSpec("sh", List.of("-c", "rm -rf /"), null)))
                .isInstanceOf(CommandNotAllowedException.class);
    }

    @Test
    void execute_rejectsNonWhitelistedCommandBeforeRunningAny() {
        List<String> executed = new ArrayList<>();
        CommandGate gate = new CommandGate((command, dir) -> {
            executed.add(command.executable());
            return success(command);
        }, propertiesWith("mvn"));

        assertThatThrownBy(() -> gate.execute(List.of(
                new CommandSpec("mvn", List.of("test"), null),
                new CommandSpec("curl", List.of("http://evil.example"), null)), tempDir))
                .isInstanceOf(CommandNotAllowedException.class);

        assertThat(executed).isEmpty();
    }

    @Test
    void execute_allowsCommitAndPushWhenAllAcceptanceCommandsPass() {
        CommandGate gate = new CommandGate((command, dir) -> success(command), propertiesWith("mvn", "pnpm"));

        CommandGateResult result = gate.execute(List.of(
                new CommandSpec("mvn", List.of("test"), null),
                new CommandSpec("pnpm", List.of("test"), null)), tempDir);

        assertThat(result.isComplete()).isTrue();
        assertThat(result.isAllPassed()).isTrue();
        assertThat(result.isCommitAllowed()).isTrue();
        assertThat(result.isPushAllowed()).isTrue();
        assertThat(result.failure()).isNull();
        assertThat(result.steps()).hasSize(2);
    }

    @Test
    void execute_blocksCommitAndPushWhenAcceptanceCommandExitsNonZero() {
        List<String> executed = new ArrayList<>();
        CommandGate gate = new CommandGate((command, dir) -> {
            executed.add(command.executable());
            if ("pnpm".equals(command.executable())) {
                return new CommandStepResult(command.executable(), command.arguments(), 1, "failed", false, 10L);
            }
            return success(command);
        }, propertiesWith("mvn", "pnpm", "gradle"));

        CommandGateResult result = gate.execute(List.of(
                new CommandSpec("mvn", List.of("test"), null),
                new CommandSpec("pnpm", List.of("test"), null),
                new CommandSpec("gradle", List.of("test"), null)), tempDir);

        assertThat(result.isAllPassed()).isFalse();
        assertThat(result.isCommitAllowed()).isFalse();
        assertThat(result.isPushAllowed()).isFalse();
        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().exitCode()).isEqualTo(1);
        assertThat(result.steps()).hasSize(2);
        assertThat(executed).containsExactly("mvn", "pnpm");
    }

    @Test
    void execute_runsCommandsSequentiallyInDeclaredOrder() {
        List<String> executed = new ArrayList<>();
        CommandGate gate = new CommandGate((command, dir) -> {
            executed.add(command.executable());
            return success(command);
        }, propertiesWith("mvn", "pnpm", "gradle"));

        gate.execute(List.of(
                new CommandSpec("mvn", List.of("test"), null),
                new CommandSpec("pnpm", List.of("test"), null),
                new CommandSpec("gradle", List.of("test"), null)), tempDir);

        assertThat(executed).containsExactly("mvn", "pnpm", "gradle");
    }

    @Test
    void execute_withRealProcess_blocksCommitOnNonZeroExit() {
        CommandGate gate = new CommandGate(new ProcessCommandExecutor(), propertiesWith("java"));
        CommandSpec failing = new CommandSpec(javaExecutable(),
                List.of("-cp", System.getProperty("java.class.path"),
                        CodexRunnerTestHelper.class.getName(), "basic", "7"), null);

        CommandGateResult result = gate.execute(List.of(failing), tempDir);

        assertThat(result.isCommitAllowed()).isFalse();
        assertThat(result.isPushAllowed()).isFalse();
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).exitCode()).isEqualTo(7);
    }

    @Test
    void execute_withRealProcess_allowsCommitOnZeroExit() {
        CommandGate gate = new CommandGate(new ProcessCommandExecutor(), propertiesWith("java"));
        CommandSpec passing = new CommandSpec(javaExecutable(),
                List.of("-cp", System.getProperty("java.class.path"),
                        CodexRunnerTestHelper.class.getName(), "basic", "0"), null);

        CommandGateResult result = gate.execute(List.of(passing), tempDir);

        assertThat(result.isCommitAllowed()).isTrue();
        assertThat(result.isPushAllowed()).isTrue();
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).exitCode()).isZero();
    }

    private AgentExecProperties propertiesWith(String... allowed) {
        AgentExecProperties properties = new AgentExecProperties();
        properties.setAllowedCommands(List.of(allowed));
        return properties;
    }

    private CommandStepResult success(CommandSpec command) {
        return new CommandStepResult(command.executable(), command.arguments(), 0, "", false, 1L);
    }

    private String javaExecutable() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String name = os.contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", name).toString();
    }

}
