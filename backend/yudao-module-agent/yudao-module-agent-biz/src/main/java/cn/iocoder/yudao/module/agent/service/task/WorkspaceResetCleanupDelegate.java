package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskProjectMapper;
import cn.iocoder.yudao.module.agent.service.workspace.TaskBranchManager;
import cn.iocoder.yudao.module.agent.service.workspace.WorktreeManager;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工作区与分支的重置清理委托实现
 *
 * <p>清理顺序：先回收聚合工作区（worktree remove + 物理删除 + prune），
 * 再按任务-项目映射删除各项目的本地与远端特性分支。两类清理均幂等，
 * 资源不存在时直接跳过，保证重置流程仍能完成元数据清理。</p>
 *
 * @author TaskForge
 */
@Component
public class WorkspaceResetCleanupDelegate implements ResetCleanupDelegate {

    @Resource
    private WorktreeManager worktreeManager;

    @Resource
    private TaskBranchManager taskBranchManager;

    @Resource
    private AgentTaskProjectMapper taskProjectMapper;

    @Override
    public void cleanup(ResetCleanupContext context) {
        worktreeManager.destroyCompositeWorkspaceIfPresent(context.taskNo());

        if (context.targetBranch() == null || context.targetBranch().isBlank()) {
            return;
        }
        List<AgentTaskProjectDO> projects = taskProjectMapper.selectListByTaskId(context.taskId());
        for (AgentTaskProjectDO project : projects) {
            taskBranchManager.deleteFeatureBranch(project.getProjectCode(), context.targetBranch());
        }
    }

}
