package com.bone.claim.domain.model.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 独立盲标聚合的不变量与状态机测试（G4-1 防反馈环）。
 *
 * <p>核心判据：①一次提交；②解盲只允许发生在已提交态且终态不可变；③解盲必须注册领域事件且 {@code aiParticipated}
 * 与配对结果一致；④载荷禁区键非空（这是派发扫描的依据，清空即闸门失效）。
 */
class BlindLabelTaskTest {

  private static final Long TENANT = 42L;
  private static final Long CLAIM = 9001L;
  private static final String PAYLOAD = "{\"claimNo\":\"C-1\",\"evidenceIds\":[1,2]}";
  private static final String HASH = "a".repeat(64);
  private static final Instant NOW = Instant.parse("2026-11-05T08:00:00Z");

  private static BlindLabelTask task() {
    return BlindLabelTask.open(1L, TENANT, "BL1", CLAIM, "annotator-1", PAYLOAD, HASH);
  }

  @Test
  @DisplayName("派发：PENDING 态，字段落位")
  void openHappy() {
    BlindLabelTask task = task();
    assertThat(task.getStatus()).isEqualTo(BlindLabelTask.Status.PENDING.name());
    assertThat(task.getTaskNo()).isEqualTo("BL1");
    assertThat(task.getPayloadHash()).isEqualTo(HASH);
    assertThat(task.getLabelPayloadJson()).isEqualTo(PAYLOAD);
  }

  @Test
  @DisplayName("派发：载荷空白 / 超长 / 指纹非 hex64 / 标注人空白均拒绝")
  void openRejectsInvalidInput() {
    assertThatThrownBy(() -> BlindLabelTask.open(1L, TENANT, "BL1", CLAIM, "a", " ", HASH))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () ->
                BlindLabelTask.open(1L, TENANT, "BL1", CLAIM, "a", "{\"k\":1}".repeat(2000), HASH))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> BlindLabelTask.open(1L, TENANT, "BL1", CLAIM, "a", PAYLOAD, "short"))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> BlindLabelTask.open(1L, TENANT, "BL1", CLAIM, " ", PAYLOAD, HASH))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("提交：PENDING → LABELED，结论与时间落位")
  void submitOnce() {
    BlindLabelTask task = task();
    task.submit("{\"decision\":\"APPROVE\"}", NOW);
    assertThat(task.getStatus()).isEqualTo(BlindLabelTask.Status.LABELED.name());
    assertThat(task.getLabelResultJson()).contains("APPROVE");
    assertThat(task.getSubmittedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("提交不可改写：LABELED / UNBLINDED 再提交一律拒绝")
  void submitImmutable() {
    BlindLabelTask task = task();
    task.submit("{\"decision\":\"APPROVE\"}", NOW);
    assertThatThrownBy(() -> task.submit("{\"decision\":\"REJECT\"}", NOW))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不可改写");
  }

  @Test
  @DisplayName("提交：空白结论拒绝")
  void submitBlankRejected() {
    assertThatThrownBy(() -> task().submit(" ", NOW)).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("解盲：LABELED → UNBLINDED，写快照与配对结果，注册领域事件")
  void unblindPairs() {
    BlindLabelTask task = task();
    task.submit("{\"decision\":\"APPROVE\"}", NOW);
    BlindLabelUnblindedEvent event =
        task.unblind("{\"decisionResult\":\"AUTO_APPROVE\"}", BlindLabelTask.Agreement.PAIRED, NOW);
    assertThat(task.getStatus()).isEqualTo(BlindLabelTask.Status.UNBLINDED.name());
    assertThat(task.getAgreement()).isEqualTo("PAIRED");
    assertThat(task.getAiSuggestionJson()).contains("AUTO_APPROVE");
    assertThat(task.getUnblindedAt()).isEqualTo(NOW);
    assertThat(event.aiParticipated()).isTrue();
    assertThat(event.taskNo()).isEqualTo("BL1");
    assertThat(task.getDomainEvents()).hasSize(1);
  }

  @Test
  @DisplayName("解盲：AI 未真实参与时 AI_ABSENT，事件 aiParticipated=false，快照为空")
  void unblindAiAbsent() {
    BlindLabelTask task = task();
    task.submit("{\"decision\":\"APPROVE\"}", NOW);
    BlindLabelUnblindedEvent event = task.unblind(null, BlindLabelTask.Agreement.AI_ABSENT, NOW);
    assertThat(task.getAgreement()).isEqualTo("AI_ABSENT");
    assertThat(task.getAiSuggestionJson()).isNull();
    assertThat(event.aiParticipated()).isFalse();
  }

  @Test
  @DisplayName("解盲只允许一次：UNBLINDED 再解盲拒绝（终态不可变）")
  void unblindOnceOnly() {
    BlindLabelTask task = task();
    task.submit("{\"decision\":\"APPROVE\"}", NOW);
    task.unblind(null, BlindLabelTask.Agreement.AI_ABSENT, NOW);
    assertThatThrownBy(() -> task.unblind(null, BlindLabelTask.Agreement.AI_ABSENT, NOW))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("未提交先解盲拒绝——解盲配对只能发生在已提交态")
  void unblindRequiresLabeled() {
    assertThatThrownBy(() -> task().unblind(null, BlindLabelTask.Agreement.AI_ABSENT, NOW))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("已提交");
  }

  @Test
  @DisplayName("解盲必须显式给出配对结果（不给 null 冒充）")
  void unblindRequiresAgreement() {
    BlindLabelTask task = task();
    task.submit("{\"decision\":\"APPROVE\"}", NOW);
    assertThatThrownBy(() -> task.unblind(null, null, NOW)).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("载荷禁区键非空且含关键 AI 痕迹键——清空即闸门失效（防手滑删闸）")
  void forbiddenKeysGuard() {
    assertThat(BlindLabelTask.FORBIDDEN_PAYLOAD_KEYS)
        .contains("aiSuggestion", "aiConfidence", "confidenceSource", "ruleHits", "decisionResult");
  }
}
