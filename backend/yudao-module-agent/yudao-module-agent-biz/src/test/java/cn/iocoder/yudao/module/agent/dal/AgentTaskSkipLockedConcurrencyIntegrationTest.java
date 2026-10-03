package cn.iocoder.yudao.module.agent.dal;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-DATA-04 集成测试（并发部分）：验证 MySQL 8 的 FOR UPDATE SKIP LOCKED
 * 在多个 Worker 并发抢占时不会重复消费，且会跳过被其他事务锁定的行而不阻塞。
 */
@Testcontainers
class AgentTaskSkipLockedConcurrencyIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("taskforge_skip")
            .withUsername("taskforge")
            .withPassword("taskforge");

    @BeforeAll
    static void migrate() throws SQLException {
        try (Connection connection = newConnection()) {
            AgentMySQLTestSupport.runMigrations(connection);
        }
    }

    @Test
    void skipLockedSkipsRowLockedByAnotherTransactionWithoutBlocking() throws Exception {
        long lockedTaskId = insertTask("TASK-SKIP-LOCKED-1", 1);
        long availableTaskId = insertTask("TASK-SKIP-LOCKED-2", 2);

        try (Connection holder = newConnection();
             Connection reader = newConnection()) {
            // 事务 A 持有第一行的行锁，故意不提交
            holder.setAutoCommit(false);
            try (Statement lockStatement = holder.createStatement();
                 ResultSet locked = lockStatement.executeQuery(
                         "SELECT id FROM agent_task WHERE id = " + lockedTaskId + " FOR UPDATE")) {
                assertThat(locked.next()).isTrue();
            }

            // 事务 B 用 SKIP LOCKED 查询：若未跳过被锁行，会在 2 秒后触发锁等待超时
            reader.setAutoCommit(false);
            try (Statement readerStatement = reader.createStatement()) {
                readerStatement.execute("SET SESSION innodb_lock_wait_timeout = 2");
                List<Long> visibleIds = new ArrayList<>();
                try (ResultSet resultSet = readerStatement.executeQuery(
                        "SELECT id FROM agent_task WHERE status = 'PENDING' AND deleted = 0 "
                                + "ORDER BY priority ASC, create_time ASC FOR UPDATE SKIP LOCKED")) {
                    while (resultSet.next()) {
                        visibleIds.add(resultSet.getLong("id"));
                    }
                }
                assertThat(visibleIds)
                        .contains(availableTaskId)
                        .doesNotContain(lockedTaskId);
            }
        }
    }

    @Test
    void concurrentSkipLockedClaimsDoNotDuplicateConsumption() throws Exception {
        int taskCount = 20;
        for (int i = 0; i < taskCount; i++) {
            insertTask("TASK-CONCURRENT-" + i, i);
        }

        int workerCount = 2;
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<Future<Set<Long>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workerCount; i++) {
                String workerId = "worker-" + i;
                futures.add(executor.submit(() -> {
                    Set<Long> claimed = new HashSet<>();
                    ready.countDown();
                    start.await();
                    try (Connection connection = newConnection()) {
                        connection.setAutoCommit(false);
                        while (true) {
                            Long taskId = selectNextPendingSkipLocked(connection);
                            if (taskId == null) {
                                connection.commit();
                                break;
                            }
                            int updated = markTaskRunning(connection, taskId, workerId);
                            assertThat(updated)
                                    .as("worker %s should claim task %d exactly once", workerId, taskId)
                                    .isEqualTo(1);
                            connection.commit();
                            claimed.add(taskId);
                        }
                        return claimed;
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Set<Long> allClaimed = new HashSet<>();
            for (Future<Set<Long>> future : futures) {
                allClaimed.addAll(future.get(60, TimeUnit.SECONDS));
            }

            assertThat(allClaimed)
                    .as("every pending task must be claimed exactly once with no duplicates")
                    .hasSize(taskCount);
        } finally {
            executor.shutdownNow();
        }
    }

    private static Long selectNextPendingSkipLocked(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT id FROM agent_task WHERE status = 'PENDING' AND deleted = 0 "
                             + "ORDER BY priority ASC, create_time ASC LIMIT 1 FOR UPDATE SKIP LOCKED")) {
            return resultSet.next() ? resultSet.getLong("id") : null;
        }
    }

    private static int markTaskRunning(Connection connection, long taskId, String workerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE agent_task SET status = 'RUNNING', worker_id = ?, "
                        + "execution_generation = execution_generation + 1, update_time = NOW() "
                        + "WHERE id = ? AND status = 'PENDING' AND deleted = 0")) {
            statement.setString(1, workerId);
            statement.setLong(2, taskId);
            return statement.executeUpdate();
        }
    }

    private static long insertTask(String taskNo, int priority) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO agent_task(task_no, title, task_doc, target_branch, priority) VALUES (?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, taskNo);
            statement.setString(2, "Task " + taskNo);
            statement.setString(3, "task doc");
            statement.setString(4, "feat/" + taskNo);
            statement.setInt(5, priority);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static Connection newConnection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
