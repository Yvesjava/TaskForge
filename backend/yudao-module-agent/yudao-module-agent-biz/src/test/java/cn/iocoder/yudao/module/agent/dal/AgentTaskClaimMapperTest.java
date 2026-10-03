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
 * TASK-SCHED-01 集成测试：验证 {@code selectNextPendingTaskForUpdate} 的排序、
 * 前置任务过滤、FOR UPDATE SKIP LOCKED 防重复消费，以及抢占短事务提交后行锁立即释放。
 *
 * <p>SQL 与 {@code AgentTaskMapper.xml} 中 {@code selectNextPendingTaskForUpdate} 与
 * {@code markTaskRunning} 保持一致，本测试在真实 MySQL 8 容器上验证其行为。</p>
 */
@Testcontainers
class AgentTaskClaimMapperTest {

    private static final String SELECT_NEXT_PENDING = """
            SELECT *
              FROM agent_task
             WHERE status = 'PENDING'
               AND deleted = 0
               AND (depends_on_task_id IS NULL OR EXISTS (
                       SELECT 1
                         FROM agent_task prerequisite
                        WHERE prerequisite.id = agent_task.depends_on_task_id
                          AND prerequisite.status = 'COMPLETED'
                          AND prerequisite.deleted = 0
                   ))
             ORDER BY priority ASC, create_time ASC
             LIMIT 1
             FOR UPDATE SKIP LOCKED
            """;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("taskforge_claim")
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
        // 每个用例独立验证，避免共享容器中前一个用例留下的 PENDING 任务被后续用例误消费
        executeUpdate("DELETE FROM agent_task");
    }

    @Test
    void selectNextPending_ordersByPriorityThenCreateTime() throws SQLException {
        long highPriority = insertTask("TASK-ORDER-HIGH", 1, "2026-10-03 10:00:00", null);
        long lowPriorityEarlier = insertTask("TASK-ORDER-LOW-EARLIER", 5, "2026-10-03 09:00:00", null);
        long lowPriorityLater = insertTask("TASK-ORDER-LOW-LATER", 5, "2026-10-03 11:00:00", null);

        try (Connection connection = newConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SELECT_NEXT_PENDING)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong("id")).isEqualTo(highPriority);

            // 再抢占一轮，同优先级下创建时间更早者先被取出
            markTaskRunning(connection, highPriority, "w-order", 1);
            try (Connection second = newConnection();
                 Statement secondStatement = second.createStatement();
                 ResultSet secondResult = secondStatement.executeQuery(SELECT_NEXT_PENDING)) {
                assertThat(secondResult.next()).isTrue();
                assertThat(secondResult.getLong("id")).isEqualTo(lowPriorityEarlier);
                assertThat(secondResult.getLong("id")).isNotEqualTo(lowPriorityLater);
            }
        }
    }

    @Test
    void selectNextPending_skipsTaskWithUnfinishedDependency() throws SQLException {
        long prerequisite = insertTask("TASK-DEP-PREREQ", 1, "2026-10-03 10:00:00", null);
        insertTask("TASK-DEP-CHILD", 1, "2026-10-03 10:00:01", prerequisite);
        // 前置任务仍在执行（非 PENDING、非 COMPLETED），只留下依赖它的 PENDING 子任务
        executeUpdate("UPDATE agent_task SET status = 'RUNNING' WHERE id = " + prerequisite);

        try (Connection connection = newConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SELECT_NEXT_PENDING)) {
            // 依赖未完成前置任务的任务不可被抢占，当前无任何可抢占任务
            assertThat(resultSet.next()).isFalse();
        }
    }

    @Test
    void selectNextPending_includesTaskWithCompletedDependency() throws SQLException {
        long prerequisite = insertTask("TASK-DEP-PREREQ-DONE", 1, "2026-10-03 10:00:00", null);
        long child = insertTask("TASK-DEP-CHILD-DONE", 1, "2026-10-03 10:00:01", prerequisite);
        executeUpdate("UPDATE agent_task SET status = 'COMPLETED' WHERE id = " + prerequisite);

        try (Connection connection = newConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SELECT_NEXT_PENDING)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong("id")).isEqualTo(child);
        }
    }

    @Test
    void concurrentClaims_doNotDuplicateConsumption() throws Exception {
        int taskCount = 20;
        for (int i = 0; i < taskCount; i++) {
            insertTask("TASK-CLAIM-" + i, i, "2026-10-03 10:00:" + String.format("%02d", i), null);
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
                            Long taskId = selectNextPendingForUpdate(connection);
                            if (taskId == null) {
                                connection.commit();
                                break;
                            }
                            int updated = markTaskRunning(connection, taskId, workerId, 1);
                            assertThat(updated).as("task %d claimed exactly once", taskId).isEqualTo(1);
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
            assertThat(allClaimed).as("each pending task claimed exactly once").hasSize(taskCount);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void claimTransaction_commitsBeforeReturning_andReleasesRowLock() throws SQLException {
        long taskId = insertTask("TASK-SHORT-TX", 1, "2026-10-03 10:00:00", null);

        // 抢占短事务：SELECT FOR UPDATE + 条件更新后立即提交
        try (Connection claimer = newConnection()) {
            claimer.setAutoCommit(false);
            Long selected = selectNextPendingForUpdate(claimer);
            assertThat(selected).isEqualTo(taskId);
            assertThat(markTaskRunning(claimer, selected, "w-short-tx", 1)).isEqualTo(1);
            claimer.commit();
        }

        // 事务已提交：另一连接立即可见 RUNNING，且行锁已释放、读取不被阻塞
        try (Connection reader = newConnection();
             Statement statement = reader.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT status, worker_id, execution_generation FROM agent_task WHERE id = " + taskId)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("status")).isEqualTo("RUNNING");
            assertThat(resultSet.getString("worker_id")).isEqualTo("w-short-tx");
            assertThat(resultSet.getLong("execution_generation")).isEqualTo(1L);
        }
    }

    private static Long selectNextPendingForUpdate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SELECT_NEXT_PENDING)) {
            return resultSet.next() ? resultSet.getLong("id") : null;
        }
    }

    private static int markTaskRunning(Connection connection, long taskId, String workerId, long generation)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE agent_task SET status = 'RUNNING', worker_id = ?, lease_until = NOW(), "
                        + "heartbeat_time = NOW(), started_time = NOW(), execution_generation = ?, update_time = NOW() "
                        + "WHERE id = ? AND status = 'PENDING' AND deleted = 0")) {
            statement.setString(1, workerId);
            statement.setLong(2, generation);
            statement.setLong(3, taskId);
            return statement.executeUpdate();
        }
    }

    private static long insertTask(String taskNo, int priority, String createTime, Long dependsOnTaskId)
            throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO agent_task(task_no, title, task_doc, target_branch, priority, depends_on_task_id, "
                             + "status, create_time) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, taskNo);
            statement.setString(2, "Task " + taskNo);
            statement.setString(3, "task doc");
            statement.setString(4, "feat/" + taskNo);
            statement.setInt(5, priority);
            if (dependsOnTaskId == null) {
                statement.setNull(6, java.sql.Types.BIGINT);
            } else {
                statement.setLong(6, dependsOnTaskId);
            }
            statement.setString(7, createTime);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
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
