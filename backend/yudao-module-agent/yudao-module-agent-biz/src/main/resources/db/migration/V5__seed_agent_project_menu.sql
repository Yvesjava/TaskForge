-- TaskForge 权限路由 V5：项目管理页面菜单与按钮权限
-- 契约来源：docs/技术开发与架构设计文档.md §5.1、AgentProjectController 的 @PreAuthorize。
-- 回滚：删除本脚本写入的 agent:project:* 菜单记录。

-- 1. 顶级目录：研发智能体（/agent）
INSERT INTO `system_menu` (
    `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
    `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`
) VALUES (
    '研发智能体', '', 1, 90, 0, '/agent', 'ep:cpu', NULL, NULL,
    0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'
);
SET @agent_root_id = LAST_INSERT_ID();

-- 2. 菜单：项目管理（/agent/project）
INSERT INTO `system_menu` (
    `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
    `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`
) VALUES (
    '项目管理', 'agent:project:query', 2, 1, @agent_root_id, 'project', 'ep:folder', 'agent/project/index', 'AgentProject',
    0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'
);
SET @agent_project_id = LAST_INSERT_ID();

-- 3. 按钮权限：与后端 @PreAuthorize('@ss.hasPermission(...)') 保持一致
INSERT INTO `system_menu` (
    `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
    `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`
) VALUES
    ('项目查询', 'agent:project:query', 3, 1, @agent_project_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
    ('项目新增', 'agent:project:create', 3, 2, @agent_project_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
    ('项目修改', 'agent:project:update', 3, 3, @agent_project_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
    ('项目删除', 'agent:project:delete', 3, 4, @agent_project_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0');
