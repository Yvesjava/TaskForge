package cn.iocoder.yudao.module.agent.dal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Testcontainers MySQL 集成测试的公共辅助：按顺序执行四类 Flyway 迁移脚本。
 *
 * <p>迁移脚本位于 src/main/resources/db/migration，与生产 taskforge profile 使用同一份 DDL，
 * 因此本测试验证的是真实 MySQL 上的唯一键、索引与 FOR UPDATE SKIP LOCKED 行为。</p>
 */
final class AgentMySQLTestSupport {

    private AgentMySQLTestSupport() {
    }

    static final String[] MIGRATION_SCRIPTS = {
            "/db/migration/V1__create_agent_project.sql",
            "/db/migration/V2__create_agent_task.sql",
            "/db/migration/V3__create_agent_task_project.sql",
            "/db/migration/V4__create_agent_task_operation_log.sql"
    };

    static void runMigrations(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String script : MIGRATION_SCRIPTS) {
                for (String sql : splitStatements(readClasspath(script))) {
                    statement.execute(sql);
                }
            }
        }
    }

    private static String readClasspath(String resource) {
        try (InputStream in = AgentMySQLTestSupport.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read migration script " + resource, e);
        }
    }

    /**
     * 去除行注释后按分号拆分。当前 V1-V4 迁移脚本均不包含字符串字面量中的分号。
     */
    private static List<String> splitStatements(String script) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : script.split("\\R")) {
            if (line.trim().startsWith("--")) {
                continue;
            }
            cleaned.append(line).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for (String part : cleaned.toString().split(";")) {
            String sql = part.trim();
            if (!sql.isEmpty()) {
                statements.add(sql);
            }
        }
        return statements;
    }
}
