package com.bone.procurement.governance.domain.model.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.governance.domain.service.OversightThresholds;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 人类监督有效性领域裁决：fail-closed 三态，识别三类监督失真，任何判词都不声明模型/监督达标。 */
class OversightSignalTest {

  private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

  @Test
  void 阈值未配置时即使存在风险也不可判定() {
    OversightSignal signal =
        OversightSignal.evaluate("M2", 20, 10, 5, 3, OversightThresholds.unconfigured(), NOW);
    assertThat(signal.verdict()).isEqualTo(OversightVerdict.UNDETERMINED);
    assertThat(signal.reasonCode()).isEqualTo(OversightSignal.REASON_THRESHOLD_NOT_CONFIGURED);
    assertThat(signal.claimsModelHealthy()).isFalse();
  }

  @Test
  void 样本不足时不可判定() {
    OversightSignal signal =
        OversightSignal.evaluate("M2", 2, 2, 2, 2, new OversightThresholds(1000L, 3, 0.3), NOW);
    assertThat(signal.verdict()).isEqualTo(OversightVerdict.UNDETERMINED);
    assertThat(signal.reasonCode()).isEqualTo(OversightSignal.REASON_INSUFFICIENT_SAMPLE);
  }

  @Test
  void 无理由推翻触阈需复核() {
    OversightSignal signal =
        OversightSignal.evaluate("M2", 20, 0, 5, 0, new OversightThresholds(1000L, 3, 0.3), NOW);
    assertThat(signal.verdict()).isEqualTo(OversightVerdict.REVIEW_REQUIRED);
    assertThat(signal.verdict().isActionable()).isTrue();
    assertThat(signal.reasonCode()).isEqualTo(OversightSignal.REASON_UNREASONED_OVERRIDE);
    assertThat(signal.unreasonedOverrideCount()).isEqualTo(5);
    assertThat(signal.claimsModelHealthy()).isFalse();
  }

  @Test
  void 未回流评测触阈需复核() {
    OversightSignal signal =
        OversightSignal.evaluate("M2", 20, 0, 0, 5, new OversightThresholds(1000L, 3, 0.3), NOW);
    assertThat(signal.verdict()).isEqualTo(OversightVerdict.REVIEW_REQUIRED);
    assertThat(signal.reasonCode()).isEqualTo(OversightSignal.REASON_FEEDBACK_NOT_RAISED);
    assertThat(signal.feedbackNotRaisedCount()).isEqualTo(5);
  }

  @Test
  void 无理由推翻优先于未回流评测() {
    OversightSignal signal =
        OversightSignal.evaluate("M2", 20, 0, 2, 8, new OversightThresholds(1000L, 3, 0.3), NOW);
    assertThat(signal.verdict()).isEqualTo(OversightVerdict.REVIEW_REQUIRED);
    assertThat(signal.reasonCode()).isEqualTo(OversightSignal.REASON_UNREASONED_OVERRIDE);
  }

  @Test
  void 橡皮图章占比等于阈值触阈() {
    OversightSignal signal =
        OversightSignal.evaluate("M2", 10, 3, 0, 0, new OversightThresholds(1000L, 3, 0.3), NOW);
    assertThat(signal.verdict()).isEqualTo(OversightVerdict.REVIEW_REQUIRED);
    assertThat(signal.reasonCode()).isEqualTo(OversightSignal.REASON_FAST_REVIEW_RATIO);
    assertThat(signal.fastReviewCount()).isEqualTo(3);
  }

  @Test
  void 无风险信号不构成背书() {
    OversightSignal signal =
        OversightSignal.evaluate("M2", 20, 1, 0, 0, new OversightThresholds(1000L, 3, 0.3), NOW);
    assertThat(signal.verdict()).isEqualTo(OversightVerdict.NO_ACTIONABLE_SIGNAL);
    assertThat(signal.reasonCode()).isEqualTo(OversightSignal.REASON_NO_ACTIONABLE_SIGNAL);
    assertThat(signal.claimsModelHealthy()).isFalse();
  }

  @Test
  void 非法阈值与非法计数被拒绝() {
    assertThatThrownBy(() -> new OversightThresholds(-1L, 3, 0.3))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OversightThresholds(1000L, 0, 0.3))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OversightThresholds(1000L, 3, 1.5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                OversightSignal.evaluate(
                    "M2", 10, 0, 0, 11, new OversightThresholds(1000L, 3, 0.3), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 零样本不可判定且未配置判定为configured为假() {
    OversightSignal zero =
        OversightSignal.evaluate("M2", 0, 0, 0, 0, new OversightThresholds(1000L, 3, 0.3), NOW);
    assertThat(zero.verdict()).isEqualTo(OversightVerdict.UNDETERMINED);
    assertThat(OversightThresholds.unconfigured().configured()).isFalse();
    assertThat(new OversightThresholds(1000L, 3, 0.3).configured()).isTrue();
  }
}
