-- TaskForge 数据底座 V2：AI 研发任务主表 agent_task
-- 契约来源：docs/技术开发与架构设计文档.md §2.2
-- 回滚：db/rollback/rollback-all-agent-tables.sql（U2 段落）
CREATE TABLE `agent_task`
(
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_no`              VARCHAR(64)  NOT NULL COMMENT '任务唯一编号(如 TASK-20261001-001)',
    `title`                VARCHAR(255) NOT NULL COMMENT '任务简述',
    `status`               VARCHAR(48)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING, PAUSED, RUNNING, WAITING_ACCEPTANCE, ACCEPTED, COMPLETED, REJECTED, CANCELED, DELETED, FAILED, RESETTING, MERGE_CONFLICT_PENDING_MANUAL',
    `priority`             INT          NOT NULL DEFAULT 100 COMMENT '执行优先级(数值越小越优先)',
    `task_doc`             LONGTEXT     NOT NULL COMMENT '当前任务需求文档(含计划与验收标准)',
    `doc_version`          INT          NOT NULL DEFAULT 1 COMMENT '文档版本号(乐观锁)',
    `depends_on_task_id`   BIGINT       NULL COMMENT '可选的前置任务ID，前置任务完成后才允许调度',
    `workspace_path`       VARCHAR(255) NULL COMMENT '宿主机物理工作区绝对路径',
    `target_branch`        VARCHAR(128) NOT NULL COMMENT '本次任务创建的特性分支',
    `timeout_minutes`      INT          NOT NULL DEFAULT 30 COMMENT '超时时长(分钟)',
    `cancel_reason`        VARCHAR(255) NULL COMMENT '取消原因',
    `execution_log`        LONGTEXT     NULL COMMENT 'Codex运行及自测输出日志',
    `diff_stat`            TEXT         NULL COMMENT 'Git Diff变更统计JSON',
    `retry_times`          INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    `cost_ms`              BIGINT       NOT NULL DEFAULT 0 COMMENT '总耗时(毫秒)',
    `worker_id`            VARCHAR(128) NULL COMMENT '当前租约持有者',
    `lease_until`          DATETIME     NULL COMMENT 'Worker租约到期时间',
    `execution_generation` BIGINT       NOT NULL DEFAULT 0 COMMENT '执行代次，防止旧Worker覆盖新结果',
    `heartbeat_time`       DATETIME     NULL COMMENT '最近一次Worker心跳时间',
    `started_time`         DATETIME     NULL COMMENT '开始执行时间',
    `finished_time`        DATETIME     NULL COMMENT '完成时间',
    `creator`              VARCHAR(64)  NULL     DEFAULT '' COMMENT '创建者',
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`              VARCHAR(64)  NULL     DEFAULT '' COMMENT '更新者',
    `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              BIT(1)       NOT NULL DEFAULT b'0' COMMENT '软删除标识',
    `tenant_id`            BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`, `deleted`, `tenant_id`),
    INDEX `idx_scheduler` (`deleted`, `status`, `priority`, `create_time`),
    INDEX `idx_depends_on_task` (`depends_on_task_id`),
    INDEX `idx_lease_recovery` (`status`, `lease_until`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI研发任务主表';
