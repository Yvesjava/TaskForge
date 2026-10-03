package cn.iocoder.yudao.module.agent.service.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.List;

/**
 * 聚合工作区创建结果
 *
 * @author TaskForge
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompositeWorkspace {

    /**
     * 任务唯一编号
     */
    private String taskNo;

    /**
     * 本次任务创建的特性分支
     */
    private String targetBranch;

    /**
     * 聚合根目录
     */
    private Path root;

    /**
     * 已挂载的项目工作树
     */
    private List<MountedProject> projects;

}
