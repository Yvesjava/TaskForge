package cn.iocoder.yudao.module.agent.dal;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-DATA-04 集成测试（约束部分）：在真实 MySQL 8 容器中验证唯一键与索引。
 *
 * <p>验证对象与 docs/技术开发与架构设计文档.md §2 及四类迁移脚本保持一致：</p>
 * <ul>
 *     <li>uk_project_code(project_code, deleted, tenant_id)</li>
 *     <li>uk_task_no(task_no, deleted, tenant_id)</li>
 *     <li>uk_task_request(task_id, request_idempotency_key, tenant_id)</li>
 *     <li>idx_scheduler / idx_depends_on_task / idx_lease_recovery / idx_task_id / idx_task_time</li>
 * </ul>
 */
@Testcontainers
class AgentDataConstraintIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("taskforge")
            .withUsername("taskforge")
            .withPassword("taskforge");

    @BeforeAll
    static void migrate() throws SQLException {
        try (Connection connection = newConnection()) {
            AgentMySQLTestSupport.runMigrations(connection);
        }
    }

    @Test
    void projectCodeUniqueKeyRejectsDuplicateWithinTenantAndAllowsOtherTenantOrDeleted() throws SQLException {
        executeUpdate("INSERT INTO agent_project(project_code, name, git_url, default_branch, build_tool, tenant_id) "
                + "VALUES ('svc-a', 'Service A', 'git@example.com/a.git', 'main', 'MAVEN', 0)");

        assertDuplicateRejected(() -> executeUpdate(
                "INSERT INTO agent_project(project_code, name, git_url, default_branch, build_tool, tenant_id) "
                        + "VALUES ('svc-a', 'Service A Duplicate', 'git@example.com/a2.git', 'main', 'MAVEN', 0)"));

        // 唯一键包含 tenant_id，不同租户可以共存
        executeUpdate("INSERT INTO agent_project(project_code, name, git_url, default_branch, build_tool, tenant_id) "
                + "VALUES ('svc-a', 'Service A Tenant 1', 'git@example.com/a3.git', 'main', 'MAVEN', 1)");

        // 唯一键包含 deleted，同租户的软删除记录可以再次存在
        executeUpdate("INSERT INTO agent_project(project_code, name, git_url, default_branch, build_tool, tenant_id, deleted) "
                + "VALUES ('svc-a', 'Service A Deleted', 'git@example.com/a4.git', 'main', 'MAVEN', 0, b'1')");
    }

    @Test
    void taskNoUniqueKeyRejectsDuplicateWithinTenantAndAllowsOtherTenant() throws SQLException {
        executeUpdate("INSERT INTO agent_task(task_no, title, task_doc, target_branch, tenant_id) "
                + "VALUES ('TASK-001', 'Task One', 'doc', 'feat/x', 0)");

        assertDuplicateRejected(() -> executeUpdate(
                "INSERT INTO agent_task(task_no, title, task_doc, target_branch, tenant_id) "
                        + "VALUES ('TASK-001', 'Task One Duplicate', 'doc', 'feat/x', 0)"));

        executeUpdate("INSERT INTO agent_task(task_no, title, task_doc, target_branch, tenant_id) "
                + "VALUES ('TASK-001', 'Task One Tenant 1', 'doc', 'feat/x', 1)");
    }

    @Test
    void operationLogRequestKeyIsUniquePerTaskAndTenant() throws SQLException {
        executeUpdate("INSERT INTO agent_task_operation_log(task_id, action, request_idempotency_key, tenant_id) "
                + "VALUES (1, 'SUBMIT', 'req-1', 0)");

        assertDuplicateRejected(() -> executeUpdate(
                "INSERT INTO agent_task_operation_log(task_id, action, request_idempotency_key, tenant_id) "
                        + "VALUES (1, 'SUBMIT', 'req-1', 0)"));

        // 同一任务不同幂等键可共存
        executeUpdate("INSERT INTO agent_task_operation_log(task_id, action, request_idempotency_key, tenant_id) "
                + "VALUES (1, 'SUBMIT', 'req-2', 0)");

        // 唯一键包含 tenant_id，不同租户可共存
        executeUpdate("INSERT INTO agent_task_operation_log(task_id, action, request_idempotency_key, tenant_id) "
                + "VALUES (1, 'SUBMIT', 'req-1', 1)");
    }

    @Test
    void requiredUniqueKeysAndIndexesExistInInformationSchema() throws SQLException {
        assertIndexExists("agent_project", "uk_project_code");

        assertIndexExists("agent_task", "uk_task_no");
        assertIndexExists("agent_task", "idx_scheduler");
        assertIndexExists("agent_task", "idx_depends_on_task");
        assertIndexExists("agent_task", "idx_lease_recovery");

        assertIndexExists("agent_task_project", "idx_task_id");

        assertIndexExists("agent_task_operation_log", "uk_task_request");
        assertIndexExists("agent_task_operation_log", "idx_task_time");
    }

    private static void assertIndexExists(String table, String index) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.STATISTICS "
                             + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?")) {
            statement.setString(1, table);
            statement.setString(2, index);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                assertThat(resultSet.getInt(1))
                        .as("index %s on table %s should exist", index, table)
                        .isGreaterThan(0);
            }
        }
    }

    private static void assertDuplicateRejected(ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isInstanceOf(SQLException.class)
                .satisfies(throwable -> assertThat(((SQLException) throwable).getSQLState())
                        .as("duplicate insert should raise a SQL integrity constraint violation")
                        .startsWith("23"));
    }

    private static void executeUpdate(String sql) throws SQLException {
        try (Connection connection = newConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static Connection newConnection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
