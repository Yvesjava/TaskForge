-- TaskForge 数据底座 V4：任务操作审计表 agent_task_operation_log
-- 契约来源：docs/技术开发与架构设计文档.md §2.4
-- 回滚：db/rollback/rollback-all-agent-tables.sql（U4 段落）
CREATE TABLE `agent_task_operation_log`
(
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`                 BIGINT       NOT NULL COMMENT '任务ID',
    `action`                  VARCHAR(32)  NOT NULL COMMENT '操作类型: SUBMIT, PAUSE, EDIT, RESUME, CANCEL, RESET, RE_ENQUEUE, CLAIM, HEARTBEAT_EXPIRED, SELF_VERIFY_PASS, ACCEPT, REJECT, MERGE_PASS, MERGE_CONFLICT, CLEANUP_PASS, DELETE',
    `from_status`             VARCHAR(48)  NULL COMMENT '操作前状态',
    `to_status`               VARCHAR(48)  NULL COMMENT '操作后状态',
    `doc_version`             INT          NULL COMMENT '操作涉及的文档版本',
    `feedback`                TEXT         NULL COMMENT '打回或取消原因',
    `request_idempotency_key` VARCHAR(128) NOT NULL COMMENT '请求幂等键',
    `operator_id`             BIGINT       NULL COMMENT '操作人',
    `operator_name`           VARCHAR(128) NULL COMMENT '操作人名称快照',
    `payload`                 JSON         NULL COMMENT '经过脱敏的操作参数',
    `create_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `tenant_id`               BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_request` (`task_id`, `request_idempotency_key`, `tenant_id`),
    INDEX `idx_task_time` (`task_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI研发任务操作审计表';
