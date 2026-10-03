package cn.iocoder.yudao.module.agent.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 通知投递 Outbox DO
 *
 * <p>Webhook 首次投递失败后写入 Outbox，由补偿任务跨运行周期重试，直到成功或达到
 * 最大重试次数。{@code webhook_url} 可能携带群机器人密钥，禁止写入日志。</p>
 *
 * @author TaskForge
 */
@TableName("agent_notice_outbox")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentNoticeOutboxDO extends TenantBaseDO {

    /**
     * 主键 ID
     */
    @TableId
    private Long id;
    /**
     * 关联任务编号
     */
    private String taskNo;
    /**
     * 通知事件：WAITING_ACCEPTANCE、FAILED、MERGE_CONFLICT_PENDING_MANUAL
     */
    private String event;
    /**
     * 通知平台：WECOM、FEISHU
     */
    private String platform;
    /**
     * 群机器人 Webhook 地址（可能含密钥，禁止写入日志）
     */
    private String webhookUrl;
    /**
     * 已渲染的消息 JSON
     */
    private String payload;
    /**
     * 状态：PENDING、SENT、FAILED
     */
    private String status;
    /**
     * 跨运行周期重试次数
     */
    private Integer retryCount;
    /**
     * 最大重试次数
     */
    private Integer maxRetries;
    /**
     * 下次重试时间；为 NULL 表示终态
     */
    private LocalDateTime nextRetryTime;
    /**
     * 最近一次失败原因（已脱敏）
     */
    private String lastError;

}
