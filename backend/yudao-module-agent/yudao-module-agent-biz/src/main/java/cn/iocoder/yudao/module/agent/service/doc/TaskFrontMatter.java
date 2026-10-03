package cn.iocoder.yudao.module.agent.service.doc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 任务文档 YAML Front Matter 的类型化模型
 *
 * <p>必填字段：{@code taskId}、{@code title}、{@code targetBranch}、{@code timeoutMinutes}；
 * 单仓任务使用 {@code repoUrl}、{@code baseBranch}；多仓任务使用 {@code projects} 列表。
 * {@code priority}、{@code dependsOnTaskId} 为可选字段。
 *
 * <p>必填性与业务校验由后续文档校验任务负责，本模型只承载解析后的类型化字段。
 *
 * @author TaskForge
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskFrontMatter {

    /**
     * 任务唯一编号（如 TASK-20261001-088）
     */
    private String taskId;

    /**
     * 任务简述
     */
    private String title;

    /**
     * 本次任务创建的特性分支
     */
    private String targetBranch;

    /**
     * 超时时长（分钟）
     */
    private Integer timeoutMinutes;

    /**
     * 执行优先级（可选，数值越小越优先）
     */
    private Integer priority;

    /**
     * 可选的前置任务 ID
     */
    private Long dependsOnTaskId;

    /**
     * 单仓任务 Git 仓库地址（可选）
     */
    private String repoUrl;

    /**
     * 单仓任务基线分支（可选）
     */
    private String baseBranch;

    /**
     * 多仓任务项目引用列表（可选）
     */
    private List<TaskProjectRef> projects;

}
