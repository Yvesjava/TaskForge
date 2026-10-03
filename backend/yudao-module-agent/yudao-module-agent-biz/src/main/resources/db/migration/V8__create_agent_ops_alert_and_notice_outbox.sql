-- TaskForge 数据底座 V8：补偿任务告警记录表与通知投递 Outbox 表
-- 契约来源：docs/技术开发与架构设计文档.md（OPS 补偿任务与告警）
-- 回滚：db/rollback/rollback-all-agent-tables.sql（U8 段落）
CREATE TABLE `agent_ops_alert`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `alert_type`   VARCHAR(48)  NOT NULL COMMENT '告警类型: RESIDUAL_WORKSPACE/EXPIRED_LEASE/FAILED_NOTICE',
    `level`        VARCHAR(16)  NOT NULL DEFAULT 'WARN' COMMENT '告警级别: INFO/WARN/ERROR',
    `task_no`      VARCHAR(64)  NULL COMMENT '关联任务编号',
    `resource_key` VARCHAR(255) NULL COMMENT '关联资源标识（工作区路径/任务ID/Outbox ID）',
    `message`      VARCHAR(512) NOT NULL COMMENT '告警摘要',
    `detail`       TEXT         NULL COMMENT '脱敏后的告警详情',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
    `tenant_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_alert_type_time` (`alert_type`, `create_time`),
    INDEX `idx_alert_task` (`task_no`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='TaskForge 补偿任务告警记录表';

CREATE TABLE `agent_notice_outbox`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_no`         VARCHAR(64)  NOT NULL COMMENT '关联任务编号',
    `event`           VARCHAR(48)  NOT NULL COMMENT '通知事件: WAITING_ACCEPTANCE/FAILED/MERGE_CONFLICT_PENDING_MANUAL',
    `platform`        VARCHAR(32)  NOT NULL COMMENT '通知平台: WECOM/FEISHU',
    `webhook_url`     VARCHAR(512) NOT NULL COMMENT '群机器人 Webhook 地址（可能含密钥，禁止写入日志）',
    `payload`         TEXT         NOT NULL COMMENT '已渲染的消息 JSON',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SENT/FAILED',
    `retry_count`     INT          NOT NULL DEFAULT 0 COMMENT '跨运行周期重试次数',
    `max_retries`     INT          NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    `next_retry_time` DATETIME     NULL COMMENT '下次重试时间；NULL 表示终态',
    `last_error`      VARCHAR(512) NULL COMMENT '最近一次失败原因（已脱敏）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         BIT(1)       NOT NULL DEFAULT b'0' COMMENT '软删除标识',
    `tenant_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_outbox_dispatch` (`status`, `next_retry_time`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='TaskForge 通知投递 Outbox 表';
