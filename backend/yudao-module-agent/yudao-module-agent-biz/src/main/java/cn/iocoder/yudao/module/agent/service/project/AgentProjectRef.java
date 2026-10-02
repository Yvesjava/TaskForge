package cn.iocoder.yudao.module.agent.service.project;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务文档中引用的项目映射
 *
 * <p>投递与恢复排队前，由 {@link AgentProjectRefValidator} 统一校验
 * 项目是否存在且启用、基线分支是否合法、子目录映射是否冲突。
 *
 * @author TaskForge
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentProjectRef {

    /**
     * 项目代号（如 backend-service）
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

}
