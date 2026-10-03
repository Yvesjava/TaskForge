package cn.iocoder.yudao.module.agent.service.ops;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.agent.enums.AgentAlertType;
import cn.iocoder.yudao.module.agent.framework.ops.AgentCompensationProperties;
import cn.iocoder.yudao.module.agent.framework.secret.SecretRedactor;
import cn.iocoder.yudao.module.agent.service.scheduler.AgentTaskLeaseRecoveryService;
import cn.iocoder.yudao.module.agent.service.workspace.WorktreeManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * 补偿任务服务实现
 *
 * @author TaskForge
 */
@Service
@Slf4j
public class AgentCompensationServiceImpl implements AgentCompensationService {

    @Resource
    private WorktreeManager worktreeManager;

    @Resource
    private AgentTaskLeaseRecoveryService leaseRecoveryService;

    @Resource
    private AgentNoticeOutboxService noticeOutboxService;

    @Resource
    private AgentAlertService alertService;

    @Resource
    private AgentCompensationProperties properties;

    @Override
    public AgentCompensationResult compensate() {
        // 补偿任务跨租户扫描全部资源，与调度器/租约恢复的全局视角保持一致
        return TenantUtils.executeIgnore(() -> {
            int residualCleaned = compensateResidualWorkspaces();
            int expiredRecovered = compensateExpiredLeases();
            AgentNoticeDispatchResult noticeResult = noticeOutboxService.dispatchDue(
                    properties.getNoticeDispatchBatchSize());
            recordNoticeAlert(noticeResult);
            return new AgentCompensationResult(residualCleaned, expiredRecovered,
                    noticeResult.sent(), noticeResult.failed());
        });
    }

    private int compensateResidualWorkspaces() {
        List<Path> residues = worktreeManager.detectResidue();
        int cleaned = 0;
        for (Path residue : residues) {
            String resourceKey = residue.toString();
            String taskNo = worktreeManager.taskNoOfResidue(residue);
            if (taskNo == null) {
                alertService.record(AgentAlertType.RESIDUAL_WORKSPACE, "ERROR", null, resourceKey,
                        "残留工作区无法解析任务编号，跳过清理", null);
                continue;
            }
            try {
                worktreeManager.destroyCompositeWorkspaceIfPresent(taskNo);
                cleaned++;
                alertService.record(AgentAlertType.RESIDUAL_WORKSPACE, "WARN", taskNo, resourceKey,
                        "清理残留工作区成功", null);
            } catch (Exception e) {
                String detail = SecretRedactor.describe(e, List.of());
                log.warn("[Compensation] 残留工作区清理失败 taskNo={} resourceKey={} reason={}",
                        taskNo, resourceKey, detail);
                alertService.record(AgentAlertType.RESIDUAL_WORKSPACE, "ERROR", taskNo, resourceKey,
                        "清理残留工作区失败", detail);
            }
        }
        return cleaned;
    }

    private int compensateExpiredLeases() {
        int recovered = leaseRecoveryService.recoverExpiredLeases();
        if (recovered > 0) {
            alertService.record(AgentAlertType.EXPIRED_LEASE, "WARN", null, null,
                    "租约过期任务已恢复为 FAILED", "recovered=" + recovered);
        }
        return recovered;
    }

    private void recordNoticeAlert(AgentNoticeDispatchResult result) {
        if (result.failed() > 0) {
            alertService.record(AgentAlertType.FAILED_NOTICE, "ERROR", null, null,
                    "失败通知补偿后仍有失败",
                    "failed=" + result.failed() + ", sent=" + result.sent());
        } else if (result.sent() > 0) {
            alertService.record(AgentAlertType.FAILED_NOTICE, "INFO", null, null,
                    "失败通知补偿重发成功", "sent=" + result.sent());
        }
    }

}
