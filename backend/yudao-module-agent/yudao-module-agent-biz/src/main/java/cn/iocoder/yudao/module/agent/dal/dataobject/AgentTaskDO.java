package cn.iocoder.yudao.module.agent.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AI 研发任务主表 DO
 *
 * @author TaskForge
 */
@TableName("agent_task")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTaskDO extends TenantBaseDO {

    /**
     * 主键 ID
     */
    @TableId
    private Long id;
    /**
     * 任务唯一编号（如 TASK-20261001-001）
     */
    private String taskNo;
    /**
     * 任务简述
     */
    private String title;
    /**
     * 状态：PENDING、RUNNING、WAITING_ACCEPTANCE、COMPLETED 等
     */
    private String status;
    /**
     * 执行优先级（数值越小越优先）
     */
    private Integer priority;
    /**
     * 当前任务需求文档（含计划与验收标准）
     */
    private String taskDoc;
    /**
     * 文档版本号（乐观锁）
     */
    private Integer docVersion;
    /**
     * 可选的前置任务 ID，前置任务完成后才允许调度
     */
    private Long dependsOnTaskId;
    /**
     * 宿主机物理工作区绝对路径
     */
    private String workspacePath;
    /**
     * 本次任务创建的特性分支
     */
    private String targetBranch;
    /**
     * 超时时长（分钟）
     */
    private Integer timeoutMinutes;
    /**
     * 取消原因
     */
    private String cancelReason;
    /**
     * Codex 运行及自测输出日志
     */
    private String executionLog;
    /**
     * Git Diff 变更统计 JSON
     */
    private String diffStat;
    /**
     * 重试次数
     */
    private Integer retryTimes;
    /**
     * 总耗时（毫秒）
     */
    private Long costMs;
    /**
     * 当前租约持有者
     */
    private String workerId;
    /**
     * Worker 租约到期时间
     */
    private LocalDateTime leaseUntil;
    /**
     * 执行代次，防止旧 Worker 覆盖新结果
     */
    private Long executionGeneration;
    /**
     * 最近一次 Worker 心跳时间
     */
    private LocalDateTime heartbeatTime;
    /**
     * 开始执行时间
     */
    private LocalDateTime startedTime;
    /**
     * 完成时间
     */
    private LocalDateTime finishedTime;

}
