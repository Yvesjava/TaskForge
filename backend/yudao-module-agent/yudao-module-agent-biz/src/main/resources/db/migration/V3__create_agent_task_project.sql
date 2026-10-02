-- TaskForge 数据底座 V3：任务-项目关联映射表 agent_task_project
-- 契约来源：docs/技术开发与架构设计文档.md §2.3
-- 回滚：db/rollback/rollback-all-agent-tables.sql（U3 段落）
CREATE TABLE `agent_task_project`
(
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`      BIGINT      NOT NULL COMMENT '任务ID',
    `project_id`   BIGINT      NOT NULL COMMENT '项目ID',
    `project_code` VARCHAR(64) NOT NULL COMMENT '项目代号',
    `base_branch`  VARCHAR(64) NOT NULL COMMENT '检出基线分支',
    `sub_dir`      VARCHAR(64) NOT NULL COMMENT '在聚合工作区下的子目录名',
    `merge_status` VARCHAR(32) NOT NULL DEFAULT 'UNMERGED' COMMENT '合并状态: UNMERGED, PRECHECKING, MERGING, MERGED, CONFLICT, FAILED',
    `commit_hash`  VARCHAR(64) NULL COMMENT '最终提交CommitId',
    `creator`      VARCHAR(64) NULL     DEFAULT '' COMMENT '创建者',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`      VARCHAR(64) NULL     DEFAULT '' COMMENT '更新者',
    `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      BIT(1)      NOT NULL DEFAULT b'0' COMMENT '软删除标识',
    `tenant_id`    BIGINT      NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_task_id` (`task_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='任务涉及的项目及基线关系表';
