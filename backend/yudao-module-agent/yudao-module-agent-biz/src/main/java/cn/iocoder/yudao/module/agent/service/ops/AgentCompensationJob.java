package cn.iocoder.yudao.module.agent.service.ops;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 补偿任务定时触发
 *
 * <p>以固定延迟周期调用 {@link AgentCompensationService}，执行残留工作区、
 * 过期租约与失败通知三类补偿。延迟可通过 {@code yudao.agent.compensation.fixed-delay-ms}
 * 配置覆盖。</p>
 *
 * @author TaskForge
 */
@Component
@Slf4j
public class AgentCompensationJob {

    @Resource
    private AgentCompensationService compensationService;

    @Scheduled(fixedDelayString = "${yudao.agent.compensation.fixed-delay-ms:30000}")
    public void compensate() {
        AgentCompensationResult result = compensationService.compensate();
        if (result.hasWork()) {
            log.info("[Compensation] 完成一轮补偿 residualWorkspacesCleaned={}, expiredLeasesRecovered={}, noticesSent={}, noticesFailed={}",
                    result.residualWorkspacesCleaned(), result.expiredLeasesRecovered(),
                    result.noticesSent(), result.noticesFailed());
        }
    }

}
