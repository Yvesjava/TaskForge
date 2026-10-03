package cn.iocoder.yudao.module.agent.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentOpsAlertDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 补偿任务告警记录 Mapper
 *
 * @author TaskForge
 */
@Mapper
public interface AgentOpsAlertMapper extends BaseMapperX<AgentOpsAlertDO> {

}
