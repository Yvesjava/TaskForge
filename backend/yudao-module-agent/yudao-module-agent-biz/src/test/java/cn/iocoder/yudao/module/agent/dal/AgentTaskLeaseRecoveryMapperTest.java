package cn.iocoder.yudao.module.agent.dal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * TASK-SCHED-04 集成测试：验证租约过期扫描 SQL 与旧 Worker 写回守卫在真实 MySQL 上的行为。
 *
 * <p>SQL 与 {@code AgentTaskMapper.xml} 中的 {@code selectExpiredRunningTasks}、
 * {@code markLeaseExpiredFailed}、{@code markSelfVerifiedIfRunning} 与
 * {@code markFailedIfRunning} 保持一致。</p>
 */
@Testcontainers
class AgentTaskLeaseRecoveryMapperTest {

    private static final String SELECT_EXPIRED_RUNNING = """
            SELECT *
              FROM agent_task
             WHERE status = 'RUNNING'
               AND lease_until < NOW()
               AND deleted = 0
             ORDER BY lease_until ASC
             LIMIT 50
             FOR UPDATE SKIP LOCKED
            """;

    private static final String MARK_LEASE_EXPIRED_FAILED = """
            UPDATE agent_task
               SET status = 'FAILED',
                   worker_id = NULL,
                   lease_until = NULL,
                   heartbeat_time = NULL,
                   execution_generation = ?,
                   update_time = NOW()
             WHERE id = ?
               AND status = 'RUNNING'
               AND lease_until < NOW()
               AND deleted = 0
            """;

    private static final String MARK_SELF_VERIFIED_IF_RUNNING = """
            UPDATE agent_task
               SET status = 'WAITING_ACCEPTANCE',
                   lease_until = NULL,
                   heartbeat_time = NULL,
                   execution_log = ?,
                   retry_times = ?,
                   cost_ms = ?,
                   diff_stat = ?,
                   workspace_path = ?,
                   update_time = NOW()
             WHERE id = ?
               AND status = 'RUNNING'
               AND worker_id = ?
               AND execution_generation = ?
               AND deleted = 0
            """;

    private static final String MARK_FAILED_IF_RUNNING = """
            UPDATE agent_task
               SET status = 'FAILED',
                   worker_id = NULL,
                   lease_until = NULL,
                   heartbeat_time = NULL,
                   execution_log = ?,
                   retry_times = ?,
                   cost_ms = ?,
                   diff_stat = ?,
                   workspace_path = NULL,
                   finished_time = NOW(),
                   update_time = NOW()
             WHERE id = ?
               AND status = 'RUNNING'
               AND worker_id = ?
               AND execution_generation = ?
               AND deleted = 0
            """;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("taskforge_lease_recovery")
            .withUsername("taskforge")
            .withPassword("taskforge");

    @BeforeAll
    static void migrate() throws SQLException {
        try (Connection connection = newConnection()) {
            AgentMySQLTestSupport.runMigrations(connection);
        }
    }

    @BeforeEach
    void cleanAgentTask() throws SQLException {
        executeUpdate("DELETE FROM agent_task");
    }

    @Test
    void selectExpiredRunningTasks_returnsOnlyExpiredRunningTasks() throws SQLException {
        long expired = insertRunningTask("TASK-EXPIRED", "worker-expired", 2L, "2000-01-01 00:00:00");
        insertRunningTask("TASK-ALIVE", "worker-alive", 1L, "2099-01-01 00:00:00");
        insertPendingTask("TASK-PENDING", "2026-10-03 09:00:00");

        try (Connection connection = newConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SELECT_EXPIRED_RUNNING)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong("id")).isEqualTo(expired);
            assertThat(resultSet.next()).isFalse();
        }
    }

    @Test
    void markLeaseExpiredFailed_andOldWorkerWriteBack_cannotOverwrite() throws SQLException {
        long taskId = insertRunningTask("TASK-OLD-WORKER", "worker-old", 3L, "2000-01-01 00:00:00");

        // 恢复器把执行代次推进到 4 并清空 worker
        assertThat(markLeaseExpiredFailed(taskId, 4L)).isEqualTo(1);
        assertTaskSnapshot(taskId, "FAILED", null, 4L);

        // 旧 Worker 携带旧 workerId 与旧代次写回，条件失配，不能覆盖
        assertThat(markSelfVerifiedIfRunning(taskId, "worker-old", 3L)).isZero();
        assertTaskSnapshot(taskId, "FAILED", null, 4L);

        assertThat(markFailedIfRunning(taskId, "worker-old", 3L)).isZero();
        assertTaskSnapshot(taskId, "FAILED", null, 4L);
    }

    private static long insertRunningTask(String taskNo, String workerId, long generation, String leaseUntil)
            throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO agent_task(task_no, title, task_doc, target_branch, status, worker_id, "
                             + "lease_until, execution_generation, create_time) VALUES (?, ?, ?, ?, 'RUNNING', ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, taskNo);
            statement.setString(2, "Task " + taskNo);
            statement.setString(3, "task doc");
            statement.setString(4, "feat/" + taskNo);
            statement.setString(5, workerId);
            statement.setString(6, leaseUntil);
            statement.setLong(7, generation);
            statement.setString(8, "2026-10-03 10:00:00");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static long insertPendingTask(String taskNo, String createTime) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO agent_task(task_no, title, task_doc, target_branch, status, create_time) "
                             + "VALUES (?, ?, ?, ?, 'PENDING', ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, taskNo);
            statement.setString(2, "Task " + taskNo);
            statement.setString(3, "task doc");
            statement.setString(4, "feat/" + taskNo);
            statement.setString(5, createTime);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static int markLeaseExpiredFailed(long taskId, long generation) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(MARK_LEASE_EXPIRED_FAILED)) {
            statement.setLong(1, generation);
            statement.setLong(2, taskId);
            return statement.executeUpdate();
        }
    }

    private static int markSelfVerifiedIfRunning(long taskId, String workerId, long generation) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(MARK_SELF_VERIFIED_IF_RUNNING)) {
            statement.setString(1, "old worker log");
            statement.setInt(2, 0);
            statement.setLong(3, 0L);
            statement.setString(4, null);
            statement.setString(5, "/tmp/old-workspace");
            statement.setLong(6, taskId);
            statement.setString(7, workerId);
            statement.setLong(8, generation);
            return statement.executeUpdate();
        }
    }

    private static int markFailedIfRunning(long taskId, String workerId, long generation) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(MARK_FAILED_IF_RUNNING)) {
            statement.setString(1, "old worker failure log");
            statement.setInt(2, 0);
            statement.setLong(3, 0L);
            statement.setString(4, null);
            statement.setLong(5, taskId);
            statement.setString(6, workerId);
            statement.setLong(7, generation);
            return statement.executeUpdate();
        }
    }

    private static void assertTaskSnapshot(long taskId, String status, String workerId, long generation)
            throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status, worker_id, execution_generation FROM agent_task WHERE id = ?")) {
            statement.setLong(1, taskId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("status")).isEqualTo(status);
                assertThat(resultSet.getString("worker_id")).isEqualTo(workerId);
                assertThat(resultSet.getLong("execution_generation")).isEqualTo(generation);
            }
        }
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
