package cn.iocoder.yudao.module.agent.service.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

/**
 * 已挂载的项目工作树
 *
 * @author TaskForge
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MountedProject {

    /**
     * 项目代号
     */
    private String projectCode;

    /**
     * 在聚合工作区下的子目录名
     */
    private String subDir;

    /**
     * 实际检出的特性分支
     */
    private String branch;

    /**
     * 工作树物理路径
     */
    private Path path;

}
