package cn.iocoder.yudao.module.agent.service.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作树挂载输入规格
 *
 * <p>由上层 Worker 从项目资产与任务-项目映射组装而来，
 * 直接携带仓库地址，避免 {@link WorktreeManager} 依赖数据访问层。
 *
 * @author TaskForge
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceProject {

    /**
     * 项目代号（如 backend-service）
     */
    private String projectCode;

    /**
     * Git 仓库地址
     */
    private String gitUrl;

    /**
     * 检出基线分支
     */
    private String baseBranch;

    /**
     * 在聚合工作区下的子目录名
     */
    private String subDir;

}
