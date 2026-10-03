-- TaskForge 权限路由 V7：任务列表页面菜单与按钮权限
-- 契约来源：AgentTaskController 的 @PreAuthorize 与 docs/技术开发与架构设计文档.md §3.7。
-- 回滚：删除本脚本写入的 agent:task:* 菜单记录。

-- 复用 V5 创建的顶级目录 /agent，避免重复创建
SET @agent_root_id = (
    SELECT id FROM system_menu
    WHERE path = '/agent' AND parent_id = 0 AND type = 1 AND deleted = 0
    ORDER BY id ASC
    LIMIT 1
);

-- 1. 菜单：任务列表（/agent/task）
INSERT INTO `system_menu` (
    `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
    `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`
) VALUES (
    '任务列表', 'agent:task:query', 2, 2, @agent_root_id, 'task', 'ep:list', 'agent/task/index', 'AgentTask',
    0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'
);
SET @agent_task_id = LAST_INSERT_ID();

-- 2. 按钮权限：与后端 @PreAuthorize('@ss.hasPermission(...)') 保持一致
INSERT INTO `system_menu` (
    `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
    `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`
) VALUES
    ('任务查询', 'agent:task:query', 3, 1, @agent_task_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
    ('任务暂停', 'agent:task:pause', 3, 2, @agent_task_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
    ('任务恢复', 'agent:task:resume', 3, 3, @agent_task_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
    ('任务取消', 'agent:task:cancel', 3, 4, @agent_task_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
    ('任务重置', 'agent:task:reset', 3, 5, @agent_task_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
    ('任务重投', 'agent:task:re-enqueue', 3, 6, @agent_task_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
    ('任务删除', 'agent:task:delete', 3, 7, @agent_task_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0');
