-- TaskForge 数据底座 V1：代码项目资产表 agent_project
-- 契约来源：docs/技术开发与架构设计文档.md §2.1
-- 回滚：db/rollback/rollback-all-agent-tables.sql（U1 段落）
CREATE TABLE `agent_project`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_code`   VARCHAR(64)  NOT NULL COMMENT '项目代号(如 backend-service, web-portal)',
    `name`           VARCHAR(128) NOT NULL COMMENT '项目名称',
    `git_url`        VARCHAR(255) NOT NULL COMMENT 'Git仓库地址(SSH/HTTP)',
    `default_branch` VARCHAR(64)  NOT NULL DEFAULT 'main' COMMENT '默认主干分支',
    `build_tool`     VARCHAR(32)  NOT NULL COMMENT '构建工具: MAVEN, PNPM, GRADLE, GO',
    `test_command`   VARCHAR(255) NULL COMMENT '标准测试命令(如: mvn clean test)',
    `credential_ref` VARCHAR(128) NULL COMMENT 'Secret Manager中的Git凭证引用',
    `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0-开启, 1-关闭',
    `creator`        VARCHAR(64)  NULL     DEFAULT '' COMMENT '创建者',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        VARCHAR(64)  NULL     DEFAULT '' COMMENT '更新者',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_code` (`project_code`, `deleted`, `tenant_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='代码项目资产表';
