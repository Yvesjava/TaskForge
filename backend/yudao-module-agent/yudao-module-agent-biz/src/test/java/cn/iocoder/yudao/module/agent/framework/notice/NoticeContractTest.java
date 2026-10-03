package cn.iocoder.yudao.module.agent.framework.notice;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-NOTICE-01 通知事件与卡片数据契约测试。
 *
 * <p>校验三类卡片字段集合稳定、事件编码稳定，且卡片序列化结果不含任何秘密。</p>
 */
class NoticeContractTest {

    @Test
    void noticeEventExposesStableCodesAndLabels() {
        assertThat(NoticeEvent.values()).hasSize(3);
        assertThat(NoticeEvent.valueOfCode("WAITING_ACCEPTANCE")).isEqualTo(NoticeEvent.WAITING_ACCEPTANCE);
        assertThat(NoticeEvent.valueOfCode("FAILED")).isEqualTo(NoticeEvent.FAILED);
        assertThat(NoticeEvent.valueOfCode("MERGE_CONFLICT_PENDING_MANUAL"))
                .isEqualTo(NoticeEvent.MERGE_CONFLICT_PENDING_MANUAL);
        assertThat(NoticeEvent.WAITING_ACCEPTANCE.getLabel()).isEqualTo("待验收");
        assertThat(NoticeEvent.FAILED.getLabel()).isEqualTo("执行失败");
        assertThat(NoticeEvent.MERGE_CONFLICT_PENDING_MANUAL.getLabel()).isEqualTo("合并冲突待处理");
    }

    @Test
    void waitingAcceptanceCardHasStableFieldsAndEnvelope() {
        NoticeCard card = NoticeCard.waitingAcceptance(
                "TASK-20261003-001", "示例任务", "v3",
                List.of(new NoticeBranchRef("backend", "feat/notice", "https://github.com/org/repo.git")),
                List.of("README.md", "src/Main.java"),
                "2 files changed, 10 insertions(+), 2 deletions(-)",
                "Tests run: 12, Failures: 0", 1, 15_000L);

        assertThat(card.getEvent()).isEqualTo("WAITING_ACCEPTANCE");
        assertThat(card.getStatus()).isEqualTo("待验收");
        assertThat(card.getFields().keySet())
                .containsExactlyInAnyOrderElementsOf(NoticeCardFields.WAITING_ACCEPTANCE);
        assertEnvelope(card.toView());
    }

    @Test
    void failedCardHasStableFieldsAndEnvelope() {
        NoticeCard card = NoticeCard.failed(
                "TASK-20261003-002", "失败示例", "v3",
                "2026-10-03T12:00:00+08:00", "build failed", "tail log", 1, 20_000L);

        assertThat(card.getEvent()).isEqualTo("FAILED");
        assertThat(card.getStatus()).isEqualTo("执行失败");
        assertThat(card.getFields().keySet())
                .containsExactlyInAnyOrderElementsOf(NoticeCardFields.FAILED);
        assertEnvelope(card.toView());
    }

    @Test
    void mergeConflictCardHasStableFieldsAndEnvelope() {
        NoticeCard card = NoticeCard.mergeConflict(
                "TASK-20261003-003", "冲突示例", "v3",
                List.of(new NoticeConflictRef("backend", "main", "CONFLICT")));

        assertThat(card.getEvent()).isEqualTo("MERGE_CONFLICT_PENDING_MANUAL");
        assertThat(card.getStatus()).isEqualTo("合并冲突待处理");
        assertThat(card.getFields().keySet())
                .containsExactlyInAnyOrderElementsOf(NoticeCardFields.MERGE_CONFLICT);
        assertEnvelope(card.toView());
    }

    @Test
    void redactorMasksUrlCredentialsAuthorizationTokensAndPrivateKeys() {
        assertThat(NoticeSecretRedactor.redact("https://oauth2:ghp_secret@github.com/org/repo.git"))
                .isEqualTo("https://***@github.com/org/repo.git");
        assertThat(NoticeSecretRedactor.redact("Authorization: Bearer supersecrettoken123"))
                .isEqualTo("Authorization: ***");
        assertThat(NoticeSecretRedactor.redact("password=hunter2"))
                .isEqualTo("password=***");
        assertThat(NoticeSecretRedactor.redact("token=abc123"))
                .isEqualTo("token=***");
        assertThat(NoticeSecretRedactor.redact("key AKIAIOSFODNN7EXAMPLE leaked"))
                .isEqualTo("key *** leaked");
        String pem = "-----BEGIN RSA PRIVATE KEY-----\nabcdef\n-----END RSA PRIVATE KEY-----";
        assertThat(NoticeSecretRedactor.redact(pem)).isEqualTo("***");
    }

    @Test
    void cardsNeverContainSecretsAfterAssembly() {
        NoticeCard acceptance = NoticeCard.waitingAcceptance(
                "TASK-20261003-004", "验收示例", "v4",
                List.of(new NoticeBranchRef("backend", "feat/notice",
                        "https://oauth2:ghp_secret@github.com/org/repo.git")),
                List.of("README.md"),
                "1 file changed",
                "Authorization: Bearer supersecrettoken123\npassword=hunter2",
                2, 30_000L);

        NoticeCard failed = NoticeCard.failed(
                "TASK-20261003-005", "失败示例", "v4",
                "2026-10-03T12:00:00+08:00",
                "build failed: token=abc123",
                "-----BEGIN PRIVATE KEY-----\nsecret\n-----END PRIVATE KEY-----",
                1, 45_000L);

        NoticeCard conflict = NoticeCard.mergeConflict(
                "TASK-20261003-006", "冲突示例", "v4",
                List.of(new NoticeConflictRef("backend", "main", "CONFLICT")));

        String acceptanceJson = JsonUtils.toJsonString(acceptance.toView());
        String failedJson = JsonUtils.toJsonString(failed.toView());
        String conflictJson = JsonUtils.toJsonString(conflict.toView());

        assertThat(acceptanceJson)
                .doesNotContain("ghp_secret", "supersecrettoken123", "hunter2")
                .contains("***");
        assertThat(failedJson)
                .doesNotContain("abc123", "secret")
                .contains("***");
        assertThat(conflictJson)
                .doesNotContain("secret", "***");
    }

    private static void assertEnvelope(Map<String, Object> view) {
        assertThat(view.keySet())
                .containsExactlyInAnyOrder("event", "taskNo", "title", "status", "reportVersion", "fields");
        assertThat(view.get("fields")).isInstanceOf(Map.class);
    }

}
