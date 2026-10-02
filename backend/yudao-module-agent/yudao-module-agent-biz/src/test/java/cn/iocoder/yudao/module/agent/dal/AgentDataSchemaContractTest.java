package cn.iocoder.yudao.module.agent.dal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-DATA-03 数据契约测试：校验四类 Flyway 迁移脚本的租户、软删除、乐观锁、
 * 执行代次与审计字段约束，以及唯一键和索引，与架构文档 §2 的实体定义保持一致。
 *
 * 说明：本测试在 DDL 契约层验证，不启动数据库；并发/实际索引行为的动态验证由
 * TASK-DATA-04 的 Testcontainers 集成测试负责。
 */
class AgentDataSchemaContractTest {

    private static String readSql(String resource) {
        try (InputStream in = AgentDataSchemaContractTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("missing migration resource %s", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read migration resource " + resource, e);
        }
    }

    /** 统一小写并去掉所有空白，避免书写风格差异导致误判。 */
    private static String compact(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    @Test
    void agentProjectContainsTenantSoftDeleteAuditFieldsAndUniqueKey() {
        String sql = compact(readSql("/db/migration/V1__create_agent_project.sql"));
        assertThat(sql)
                .contains("`tenant_id`bigintnotnulldefault0")
                .contains("`deleted`bit(1)notnulldefaultb'0'")
                .contains("`creator`varchar(64)")
                .contains("`create_time`datetimenotnulldefaultcurrent_timestamp")
                .contains("`updater`varchar(64)")
                .contains("`update_time`datetimenotnulldefaultcurrent_timestamponupdatecurrent_timestamp")
                .contains("uniquekey`uk_project_code`(`project_code`,`deleted`,`tenant_id`)")
                .contains("primarykey(`id`)");
    }

    @Test
    void agentTaskContainsOptimisticLockGenerationAuditFieldsAndIndexes() {
        String sql = compact(readSql("/db/migration/V2__create_agent_task.sql"));
        assertThat(sql)
                .contains("`tenant_id`bigintnotnulldefault0")
                .contains("`deleted`bit(1)notnulldefaultb'0'")
                .contains("`doc_version`intnotnulldefault1")
                .contains("`execution_generation`bigintnotnulldefault0")
                .contains("`creator`varchar(64)")
                .contains("`create_time`datetimenotnulldefaultcurrent_timestamp")
                .contains("`updater`varchar(64)")
                .contains("`update_time`datetimenotnulldefaultcurrent_timestamponupdatecurrent_timestamp")
                .contains("uniquekey`uk_task_no`(`task_no`,`deleted`,`tenant_id`)")
                .contains("index`idx_scheduler`(`deleted`,`status`,`priority`,`create_time`)")
                .contains("index`idx_depends_on_task`(`depends_on_task_id`)")
                .contains("index`idx_lease_recovery`(`status`,`lease_until`)")
                .contains("primarykey(`id`)");
    }

    @Test
    void agentTaskProjectContainsTenantSoftDeleteAuditFieldsAndIndex() {
        String sql = compact(readSql("/db/migration/V3__create_agent_task_project.sql"));
        assertThat(sql)
                .contains("`tenant_id`bigintnotnulldefault0")
                .contains("`deleted`bit(1)notnulldefaultb'0'")
                .contains("`creator`varchar(64)")
                .contains("`create_time`datetimenotnulldefaultcurrent_timestamp")
                .contains("`updater`varchar(64)")
                .contains("`update_time`datetimenotnulldefaultcurrent_timestamponupdatecurrent_timestamp")
                .contains("index`idx_task_id`(`task_id`)")
                .contains("primarykey(`id`)");
    }

    @Test
    void agentTaskOperationLogIsImmutableAuditTable() {
        String sql = compact(readSql("/db/migration/V4__create_agent_task_operation_log.sql"));
        assertThat(sql)
                .contains("`tenant_id`bigintnotnulldefault0")
                .contains("`create_time`datetimenotnulldefaultcurrent_timestamp")
                .contains("uniquekey`uk_task_request`(`task_id`,`request_idempotency_key`,`tenant_id`)")
                .contains("index`idx_task_time`(`task_id`,`create_time`)")
                .contains("primarykey(`id`)");
        assertThat(sql)
                .doesNotContain("`deleted`")
                .doesNotContain("`creator`")
                .doesNotContain("`updater`")
                .doesNotContain("`update_time`");
    }
}
