package cn.iocoder.yudao.module.agent.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 任务操作审计日志 DO
 *
 * <p>该表为不可变审计记录，只有创建时间和租户编号，不参与软删除，
 * 因此不继承 {@link cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO}。</p>
 *
 * @author TaskForge
 */
@TableName("agent_task_operation_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTaskOperationLogDO {

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
     * 操作类型：SUBMIT、PAUSE、EDIT、RESUME、CANCEL、RESET、RE_ENQUEUE、
     * CLONE_RE_ENQUEUE、CLAIM、HEARTBEAT_EXPIRED、SELF_VERIFY_PASS、ACCEPT、REJECT、
     * MERGE_PASS、MERGE_CONFLICT、MERGE_RETRY、CLEANUP_PASS、DELETE
     */
    private String action;
    /**
     * 操作前状态
     */
    private String fromStatus;
    /**
     * 操作后状态
     */
    private String toStatus;
    /**
     * 操作涉及的文档版本
     */
    private Integer docVersion;
    /**
     * 打回或取消原因
     */
    private String feedback;
    /**
     * 请求幂等键
     */
    private String requestIdempotencyKey;
    /**
     * 操作人
     */
    private Long operatorId;
    /**
     * 操作人名称快照
     */
    private String operatorName;
    /**
     * 经过脱敏的操作参数（JSON 序列化字符串）
     */
    private String payload;
    /**
     * 操作时间
     */
    private LocalDateTime createTime;
    /**
     * 租户编号
     */
    private Long tenantId;

}
