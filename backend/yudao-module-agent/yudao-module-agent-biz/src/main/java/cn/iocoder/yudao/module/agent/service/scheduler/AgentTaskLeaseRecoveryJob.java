package cn.iocoder.yudao.module.agent.service.scheduler;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 租约过期扫描定时任务
 *
 * <p>以固定延迟周期调用 {@link AgentTaskLeaseRecoveryService}，把失租的 {@code RUNNING}
 * 任务恢复为 {@code FAILED}。延迟与批大小均可通过 {@code yudao.agent.scheduler} 配置覆盖。</p>
 *
 * @author TaskForge
 */
@Component
@Slf4j
public class AgentTaskLeaseRecoveryJob {

    @Resource
    private AgentTaskLeaseRecoveryService leaseRecoveryService;

    @Scheduled(fixedDelayString = "${yudao.agent.scheduler.recovery-fixed-delay-ms:10000}")
    public void scanAndRecover() {
        int recovered = leaseRecoveryService.recoverExpiredLeases();
        if (recovered > 0) {
            log.info("[LeaseRecovery] 完成一轮租约过期扫描 recovered={}", recovered);
        }
    }

}
