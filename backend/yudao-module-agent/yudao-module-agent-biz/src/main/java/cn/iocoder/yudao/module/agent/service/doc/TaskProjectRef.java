package cn.iocoder.yudao.module.agent.service.doc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务文档 Front Matter 中多仓项目引用
 *
 * <p>多仓任务通过 {@code projects} 列表声明多个子工程，列表项至少包含
 * {@code code}、{@code baseBranch}、{@code subDir}。
 *
 * @author TaskForge
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskProjectRef {

    /**
     * 项目代号（如 backend-service）
     */
    private String code;

    /**
     * 检出基线分支
     */
    private String baseBranch;

    /**
     * 在聚合工作区下的子目录名
     */
    private String subDir;

}
