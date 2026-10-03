-- TaskForge TASK-DOC-04 V6：投递请求幂等键去重
-- 在 agent_task_operation_log 上增加 (request_idempotency_key, tenant_id) 唯一键，
-- 使同一租户内同一幂等键只能落一次审计记录，从而保证重复投递不会重复建任务。
-- 契约来源：docs/技术开发与架构设计文档.md §2.4、§3.8。
-- 回滚：删除本脚本写入的唯一键 uk_request_key。
ALTER TABLE `agent_task_operation_log`
    ADD UNIQUE KEY `uk_request_key` (`request_idempotency_key`, `tenant_id`);
