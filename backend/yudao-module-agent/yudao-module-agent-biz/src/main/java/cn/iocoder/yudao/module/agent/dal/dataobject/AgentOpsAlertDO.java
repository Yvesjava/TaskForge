package cn.iocoder.yudao.module.agent.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 补偿任务告警记录 DO
 *
 * <p>该表为不可变告警记录，仅创建时间与租户编号，不参与软删除，因此不继承
 * {@link cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO}。</p>
 *
 * @author TaskForge
 */
@TableName("agent_ops_alert")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentOpsAlertDO {

    /**
     * 主键 ID
     */
    @TableId
    private Long id;
    /**
     * 告警类型：RESIDUAL_WORKSPACE、EXPIRED_LEASE、FAILED_NOTICE
     */
    private String alertType;
    /**
     * 告警级别：INFO、WARN、ERROR
     */
    private String level;
    /**
     * 关联任务编号
     */
    private String taskNo;
    /**
     * 关联资源标识（工作区路径、任务 ID 或 Outbox ID）
     */
    private String resourceKey;
    /**
     * 告警摘要
     */
    private String message;
    /**
     * 脱敏后的告警详情
     */
    private String detail;
    /**
     * 告警时间
     */
    private LocalDateTime createTime;
    /**
     * 租户编号
     */
    private Long tenantId;

}
