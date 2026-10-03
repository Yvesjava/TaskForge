# TaskForge 数据底座迁移回滚说明

## 迁移脚本位置

| 版本 | 脚本 | 目标表 |
| --- | --- | --- |
| V1 | `db/migration/V1__create_agent_project.sql` | `agent_project` |
| V2 | `db/migration/V2__create_agent_task.sql` | `agent_task` |
| V3 | `db/migration/V3__create_agent_task_project.sql` | `agent_task_project` |
| V4 | `db/migration/V4__create_agent_task_operation_log.sql` | `agent_task_operation_log` |
| V6 | `db/migration/V6__add_agent_task_operation_log_request_key_unique.sql` | `agent_task_operation_log` |
| V8 | `db/migration/V8__create_agent_ops_alert_and_notice_outbox.sql` | `agent_ops_alert`, `agent_notice_outbox` |

脚本由 Flyway 在 `local,taskforge` 启动时按版本顺序执行，迁移历史记录在
`flyway_schema_history` 表；已应用的版本不会重复执行，因此迁移天然可重复执行。

## 回滚方式

Flyway 社区版只有 `migrate`/`info`/`validate` 等命令，**没有 `undo`**，回滚脚本放在
`db/rollback/` 目录（该目录不是 Flyway 的 `locations`，不会被自动执行），需要人工触发：

```bash
# 1. 备份（可选但生产环境强烈建议）
docker exec taskforge-mysql mysqldump -uroot -p"$TASKFORGE_DB_PASSWORD" taskforge \
  agent_project agent_task agent_task_project agent_task_operation_log > agent-tables-backup.sql

# 2. 逆序删除四张表并清理迁移历史
docker exec -i taskforge-mysql mysql -uroot -p"$TASKFORGE_DB_PASSWORD" taskforge \
  < backend/yudao-module-agent/yudao-module-agent-biz/src/main/resources/db/rollback/rollback-all-agent-tables.sql
```

回滚后重新启动后端（`--spring.profiles.active=local,taskforge`）即可重新执行 V1~V8。

## 约束与注意事项

- 回滚按 `V8 -> V6 -> V4 -> V3 -> V2 -> V1` 逆序执行，避免触发外键或业务依赖问题。
- `agent_task_operation_log` 为不可变审计表，删除后历史操作记录不可恢复，执行前必须确认已备份。
- `agent_ops_alert` 为不可变告警表，删除后历史告警不可恢复，执行前必须确认已备份。
- `rollback-all-agent-tables.sql` 末尾会删除 `flyway_schema_history` 中版本 `1`~`4`、`6` 与 `8` 的记录；
  若只想删除表而保留迁移历史，请去掉最后一条 `DELETE` 语句。
- 脚本使用 `DROP TABLE IF EXISTS`，重复执行安全。
