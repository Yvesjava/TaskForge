-- TaskForge 权限路由 V9：补齐任务控制面按钮权限。
-- 契约来源：AgentTaskController 的 @PreAuthorize（submit/update/accept/reject/merge-conflict）。
-- 回滚：删除本脚本写入的 agent:task:submit/update/accept/reject/merge-conflict 菜单记录。

SET @agent_task_id = (
    SELECT id FROM system_menu
    WHERE permission = 'agent:task:query'
      AND parent_id > 0
      AND type = 2
      AND deleted = 0
    ORDER BY id ASC
    LIMIT 1
);

INSERT INTO `system_menu` (
    `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
    `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`
) VALUES
    ('任务投递', 'agent:task:submit', 3, 8, @agent_task_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
    ('文档编辑', 'agent:task:update', 3, 9, @agent_task_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
    ('任务验收', 'agent:task:accept', 3, 10, @agent_task_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
    ('任务打回', 'agent:task:reject', 3, 11, @agent_task_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
    ('合并冲突', 'agent:task:merge-conflict', 3, 12, @agent_task_id, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0');
