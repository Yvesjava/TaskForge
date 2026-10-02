package cn.iocoder.yudao.module.agent.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 任务-项目关联映射 DO
 *
 * @author TaskForge
 */
@TableName("agent_task_project")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTaskProjectDO extends TenantBaseDO {

    /**
     * 主键 ID
     */
    @TableId
    private Long id;
    /**
     * 任务 ID
     */
    private Long taskId;
    /**
     * 项目 ID
     */
    private Long projectId;
    /**
     * 项目代号
     */
    private String projectCode;
    /**
     * 检出基线分支
     */
    private String baseBranch;
    /**
     * 在聚合工作区下的子目录名
     */
    private String subDir;
    /**
     * 合并状态：UNMERGED、PRECHECKING、MERGING、MERGED、CONFLICT、FAILED
     */
    private String mergeStatus;
    /**
     * 最终提交 CommitId
     */
    private String commitHash;

}
