-- TaskForge 四类表回滚脚本（逆序删除 V4 -> V1）
-- Flyway 社区版不提供 undo 命令，回滚必须由运维/DBA 手动执行本脚本，
-- 完整执行步骤与约束见同目录 README.md。
--
-- 用法示例：
--   docker exec -i taskforge-mysql mysql -uroot -p"$TASKFORGE_DB_PASSWORD" taskforge \
--     < backend/yudao-module-agent/yudao-module-agent-biz/src/main/resources/db/rollback/rollback-all-agent-tables.sql
--
-- 注意：删除后历史操作审计与告警记录不可恢复，生产环境请先备份。

DROP TABLE IF EXISTS `agent_notice_outbox`;
DROP TABLE IF EXISTS `agent_ops_alert`;
DROP TABLE IF EXISTS `agent_task_operation_log`;
DROP TABLE IF EXISTS `agent_task_project`;
DROP TABLE IF EXISTS `agent_task`;
DROP TABLE IF EXISTS `agent_project`;

-- 删除 TaskForge 权限菜单种子（V5/V7/V9），使 Flyway 可从头重新执行这些迁移，
-- 避免菜单记录重复插入。仅清理 TaskForge 模块自己的菜单：根目录 /agent 以及
-- 所有 agent:* 权限按钮；不触碰其他业务模块的菜单数据。
DELETE FROM `system_menu`
 WHERE `deleted` = 0
   AND (`permission` LIKE 'agent:%'
        OR (`parent_id` = 0 AND `type` = 1 AND `path` = '/agent'));

-- 清理全部 TaskForge Flyway 迁移历史（V1~V9），使应用再次启动时从 V1 开始重新执行。
-- 若目标库尚未执行过迁移（不存在 flyway_schema_history），请单独跳过本段落。
DELETE FROM `flyway_schema_history` WHERE `version` IN ('1', '2', '3', '4', '5', '6', '7', '8', '9');
