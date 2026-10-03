package cn.iocoder.yudao.module.agent.service.ops;

import cn.iocoder.yudao.module.agent.dal.dataobject.AgentOpsAlertDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentOpsAlertMapper;
import cn.iocoder.yudao.module.agent.enums.AgentAlertType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 补偿任务告警记录服务实现
 *
 * @author TaskForge
 */
@Service
public class AgentAlertServiceImpl implements AgentAlertService {

    @Resource
    private AgentOpsAlertMapper alertMapper;

    @Override
    public void record(AgentAlertType type, String level, String taskNo, String resourceKey,
                       String message, String detail) {
        Objects.requireNonNull(type, "type 不能为空");
        AgentOpsAlertDO alert = AgentOpsAlertDO.builder()
                .alertType(type.getValue())
                .level(normalizeLevel(level))
                .taskNo(taskNo)
                .resourceKey(resourceKey)
                .message(message)
                .detail(detail)
                .createTime(LocalDateTime.now())
                .build();
        alertMapper.insert(alert);
    }

    private static String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return "WARN";
        }
        return level.trim().toUpperCase();
    }

}
