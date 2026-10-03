package cn.iocoder.yudao.module.agent.service.ops;

import cn.iocoder.yudao.module.agent.enums.AgentAlertType;
import cn.iocoder.yudao.module.agent.framework.ops.AgentCompensationProperties;
import cn.iocoder.yudao.module.agent.service.scheduler.AgentTaskLeaseRecoveryService;
import cn.iocoder.yudao.module.agent.service.workspace.WorktreeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-OPS-04 补偿任务与告警单元测试
 *
 * <p>覆盖 {@link AgentCompensationServiceImpl} 对残留工作区、过期租约与失败通知
 * 三类补偿的编排，验证补偿可重复执行（幂等）且每次恢复动作都写入告警记录。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentCompensationTest {

    @Mock
    private WorktreeManager worktreeManager;

    @Mock
    private AgentTaskLeaseRecoveryService leaseRecoveryService;

    @Mock
    private AgentNoticeOutboxService noticeOutboxService;

    @Mock
    private AgentAlertService alertService;

    @Mock
    private AgentCompensationProperties properties;

    @InjectMocks
    private AgentCompensationServiceImpl compensationService;

    @Test
    void compensate_cleansResidualWorkspacesAndRecordsAlert() {
        Path first = Path.of("/data/agent-workspace/dirA-TASK-OPS-001");
        Path second = Path.of("/data/agent-workspace/dirA-TASK-OPS-002");
        when(properties.getNoticeDispatchBatchSize()).thenReturn(50);
        when(worktreeManager.detectResidue()).thenReturn(List.of(first, second));
        when(worktreeManager.taskNoOfResidue(first)).thenReturn("TASK-OPS-001");
        when(worktreeManager.taskNoOfResidue(second)).thenReturn("TASK-OPS-002");
        when(leaseRecoveryService.recoverExpiredLeases()).thenReturn(0);
        when(noticeOutboxService.dispatchDue(50))
                .thenReturn(new AgentNoticeDispatchResult(0, 0, 0));

        AgentCompensationResult result = compensationService.compensate();

        assertThat(result.residualWorkspacesCleaned()).isEqualTo(2);
        assertThat(result.expiredLeasesRecovered()).isZero();
        assertThat(result.noticesSent()).isZero();
        assertThat(result.noticesFailed()).isZero();

        verify(worktreeManager).destroyCompositeWorkspaceIfPresent("TASK-OPS-001");
        verify(worktreeManager).destroyCompositeWorkspaceIfPresent("TASK-OPS-002");
        verify(alertService, times(2)).record(eq(AgentAlertType.RESIDUAL_WORKSPACE),
                eq("WARN"), anyString(), anyString(), anyString(), any());
    }

    @Test
    void compensate_isIdempotent_whenNothingToDo() {
        when(properties.getNoticeDispatchBatchSize()).thenReturn(50);
        when(worktreeManager.detectResidue()).thenReturn(List.of());
        when(leaseRecoveryService.recoverExpiredLeases()).thenReturn(0);
        when(noticeOutboxService.dispatchDue(50))
                .thenReturn(new AgentNoticeDispatchResult(0, 0, 0));

        AgentCompensationResult result = compensationService.compensate();

        assertThat(result.hasWork()).isFalse();
        assertThat(result.residualWorkspacesCleaned()).isZero();
        assertThat(result.expiredLeasesRecovered()).isZero();
        verify(worktreeManager, never()).destroyCompositeWorkspaceIfPresent(any());
        verify(alertService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void compensate_recoversExpiredLeasesAndRecordsAlert() {
        when(properties.getNoticeDispatchBatchSize()).thenReturn(50);
        when(worktreeManager.detectResidue()).thenReturn(List.of());
        when(leaseRecoveryService.recoverExpiredLeases()).thenReturn(3);
        when(noticeOutboxService.dispatchDue(50))
                .thenReturn(new AgentNoticeDispatchResult(0, 0, 0));

        AgentCompensationResult result = compensationService.compensate();

        assertThat(result.expiredLeasesRecovered()).isEqualTo(3);
        verify(alertService).record(eq(AgentAlertType.EXPIRED_LEASE), eq("WARN"),
                any(), any(), anyString(), anyString());
    }

    @Test
    void compensate_recordsAlertWhenFailedNoticesStillFail() {
        when(properties.getNoticeDispatchBatchSize()).thenReturn(50);
        when(worktreeManager.detectResidue()).thenReturn(List.of());
        when(leaseRecoveryService.recoverExpiredLeases()).thenReturn(0);
        when(noticeOutboxService.dispatchDue(50))
                .thenReturn(new AgentNoticeDispatchResult(3, 2, 1));

        AgentCompensationResult result = compensationService.compensate();

        assertThat(result.noticesSent()).isEqualTo(2);
        assertThat(result.noticesFailed()).isEqualTo(1);
        verify(alertService).record(eq(AgentAlertType.FAILED_NOTICE), eq("ERROR"),
                any(), any(), anyString(), anyString());
    }

    @Test
    void compensate_recordsInfoAlertWhenFailedNoticesAllRecovered() {
        when(properties.getNoticeDispatchBatchSize()).thenReturn(50);
        when(worktreeManager.detectResidue()).thenReturn(List.of());
        when(leaseRecoveryService.recoverExpiredLeases()).thenReturn(0);
        when(noticeOutboxService.dispatchDue(50))
                .thenReturn(new AgentNoticeDispatchResult(2, 2, 0));

        AgentCompensationResult result = compensationService.compensate();

        assertThat(result.noticesSent()).isEqualTo(2);
        assertThat(result.noticesFailed()).isZero();
        verify(alertService).record(eq(AgentAlertType.FAILED_NOTICE), eq("INFO"),
                any(), any(), anyString(), anyString());
    }

    @Test
    void compensate_cleansSucceedingResidueAndAlertsFailingOne() {
        Path good = Path.of("/data/agent-workspace/dirA-TASK-GOOD");
        Path bad = Path.of("/data/agent-workspace/dirA-TASK-BAD");
        when(properties.getNoticeDispatchBatchSize()).thenReturn(50);
        when(worktreeManager.detectResidue()).thenReturn(List.of(good, bad));
        when(worktreeManager.taskNoOfResidue(good)).thenReturn("TASK-GOOD");
        when(worktreeManager.taskNoOfResidue(bad)).thenReturn("TASK-BAD");
        doNothing().when(worktreeManager).destroyCompositeWorkspaceIfPresent("TASK-GOOD");
        doThrow(new RuntimeException("cleanup boom"))
                .when(worktreeManager).destroyCompositeWorkspaceIfPresent("TASK-BAD");
        when(leaseRecoveryService.recoverExpiredLeases()).thenReturn(0);
        when(noticeOutboxService.dispatchDue(50))
                .thenReturn(new AgentNoticeDispatchResult(0, 0, 0));

        AgentCompensationResult result = compensationService.compensate();

        assertThat(result.residualWorkspacesCleaned()).isEqualTo(1);
        verify(worktreeManager).destroyCompositeWorkspaceIfPresent("TASK-GOOD");
        verify(alertService).record(eq(AgentAlertType.RESIDUAL_WORKSPACE), eq("WARN"),
                eq("TASK-GOOD"), anyString(), anyString(), any());
        verify(alertService).record(eq(AgentAlertType.RESIDUAL_WORKSPACE), eq("ERROR"),
                eq("TASK-BAD"), anyString(), anyString(), anyString());
    }

    @Test
    void compensate_skipsResidueWithoutTaskNoAndRecordsAlert() {
        Path unknown = Path.of("/data/agent-workspace/dirA-!!!");
        when(properties.getNoticeDispatchBatchSize()).thenReturn(50);
        when(worktreeManager.detectResidue()).thenReturn(List.of(unknown));
        when(worktreeManager.taskNoOfResidue(unknown)).thenReturn(null);
        when(leaseRecoveryService.recoverExpiredLeases()).thenReturn(0);
        when(noticeOutboxService.dispatchDue(50))
                .thenReturn(new AgentNoticeDispatchResult(0, 0, 0));

        AgentCompensationResult result = compensationService.compensate();

        assertThat(result.residualWorkspacesCleaned()).isZero();
        verify(worktreeManager, never()).destroyCompositeWorkspaceIfPresent(any());
        verify(alertService).record(eq(AgentAlertType.RESIDUAL_WORKSPACE), eq("ERROR"),
                any(), anyString(), anyString(), any());
    }

}
