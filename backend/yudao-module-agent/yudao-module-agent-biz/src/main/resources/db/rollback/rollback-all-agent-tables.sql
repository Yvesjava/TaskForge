-- TaskForge 四类表回滚脚本（逆序删除 V4 -> V1）
-- Flyway 社区版不提供 undo 命令，回滚必须由运维/DBA 手动执行本脚本，
-- 完整执行步骤与约束见同目录 README.md。
--
-- 用法示例：
--   docker exec -i taskforge-mysql mysql -uroot -p"$TASKFORGE_DB_PASSWORD" taskforge \
--     < backend/yudao-module-agent/yudao-module-agent-biz/src/main/resources/db/rollback/rollback-all-agent-tables.sql
--
-- 注意：删除后历史操作审计数据不可恢复，生产环境请先备份。

DROP TABLE IF EXISTS `agent_task_operation_log`;
DROP TABLE IF EXISTS `agent_task_project`;
DROP TABLE IF EXISTS `agent_task`;
DROP TABLE IF EXISTS `agent_project`;

-- 清理 Flyway 迁移历史中的 V1~V4，使应用再次启动时可重新执行迁移。
-- 若目标库尚未执行过迁移（不存在 flyway_schema_history），请单独跳过本段落：
--   DELETE FROM `flyway_schema_history` WHERE `version` IN ('1', '2', '3', '4');
DELETE FROM `flyway_schema_history` WHERE `version` IN ('1', '2', '3', '4');
