package cn.iocoder.yudao.module.agent.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 代码项目资产 DO
 *
 * @author TaskForge
 */
@TableName("agent_project")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentProjectDO extends TenantBaseDO {

    /**
     * 主键 ID
     */
    @TableId
    private Long id;
    /**
     * 项目代号（如 backend-service、web-portal）
     */
    private String projectCode;
    /**
     * 项目名称
     */
    private String name;
    /**
     * Git 仓库地址（SSH/HTTP）
     */
    private String gitUrl;
    /**
     * 默认主干分支
     */
    private String defaultBranch;
    /**
     * 构建工具：MAVEN、PNPM、GRADLE、GO
     */
    private String buildTool;
    /**
     * 标准测试命令（如：mvn clean test）
     */
    private String testCommand;
    /**
     * Secret Manager 中的 Git 凭证引用
     */
    private String credentialRef;
    /**
     * 状态：0-开启，1-关闭
     */
    private Integer status;

}
