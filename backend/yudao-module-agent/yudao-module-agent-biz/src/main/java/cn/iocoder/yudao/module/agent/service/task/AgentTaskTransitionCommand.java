package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.module.agent.enums.AgentTaskAction;
import cn.iocoder.yudao.module.agent.enums.AgentTaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一状态转换命令
 *
 * <p>所有状态动作最终都组装成本命令，交由 {@link AgentTaskStateMachine} 校验并执行。
 * 不同动作只使用与自身相关的字段，其余字段可为 {@code null}。</p>
 *
 * @author TaskForge
 */
@Data
@Builder
public class AgentTaskTransitionCommand {

    /**
     * 任务主键 ID
     */
    private Long taskId;
    /**
     * 任务编号（仅用于审计和错误定位，不参与状态校验）
     */
    private String taskNo;
    /**
     * 状态动作
     */
    private AgentTaskAction action;
    /**
     * 操作前状态
     */
    private AgentTaskStatus fromStatus;
    /**
     * 目标状态；单一目标时可省略，多目标时必须显式指定
     */
    private AgentTaskStatus targetStatus;

    /**
     * Worker 身份（CLAIM、SELF_VERIFY_PASS、TIMEOUT、ERROR）
     */
    private String workerId;
    /**
     * 新租约到期时间（CLAIM）
     */
    private LocalDateTime leaseUntil;
    /**
     * 执行代次（CLAIM、SELF_VERIFY_PASS、TIMEOUT、ERROR、HEARTBEAT_EXPIRED）
     */
    private Long generation;
    /**
     * 期望文档版本（EDIT 乐观锁）
     */
    private Integer expectedDocVersion;
    /**
     * 编辑后的任务文档（EDIT）
     */
    private String taskDoc;
    /**
     * 编辑后的超时时长（EDIT）
     */
    private Integer timeoutMinutes;
    /**
     * 编辑后的优先级（EDIT）
     */
    private Integer priority;
    /**
     * 编辑后的前置任务 ID（EDIT）
     */
    private Long dependsOnTaskId;
    /**
     * 取消原因（CANCEL）
     */
    private String cancelReason;
    /**
     * 打回反馈（REJECT）
     */
    private String feedback;
    /**
     * 操作涉及的文档版本快照（ACCEPT、REJECT、MERGE_CONFLICT 等人工入口）
     */
    private Integer docVersion;

    /**
     * 请求幂等键；为空时由状态机生成内部追踪键
     */
    private String requestIdempotencyKey;
    /**
     * 操作人 ID；为空时从登录上下文读取
     */
    private Long operatorId;
    /**
     * 操作人名称快照；为空时从登录上下文读取
     */
    private String operatorName;
    /**
     * 脱敏后的操作参数 JSON
     */
    private String payload;

}
