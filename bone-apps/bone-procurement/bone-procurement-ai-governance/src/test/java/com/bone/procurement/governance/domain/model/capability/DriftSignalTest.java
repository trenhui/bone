package com.bone.procurement.governance.domain.model.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 漂移信号领域裁决：fail-closed 三态 + 任何判词都不声明模型达标（监管 §6/§9）。 */
class DriftSignalTest {

  private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

  @Test
  void 阈值未配置时即使样本充足且高推翻率也不可判定() {
    DriftSignal signal = DriftSignal.evaluate("M2", 100, 80, DriftThresholds.unconfigured(), NOW);
    assertThat(signal.verdict()).isEqualTo(DriftVerdict.UNDETERMINED);
    assertThat(signal.reasonCode()).isEqualTo(DriftSignal.REASON_THRESHOLD_NOT_CONFIGURED);
    assertThat(signal.overrideRate()).isNull();
    assertThat(signal.claimsModelHealthy()).isFalse();
  }

  @Test
  void 样本不足时不可判定() {
    DriftSignal signal = DriftSignal.evaluate("M2", 2, 2, new DriftThresholds(0.3, 3), NOW);
    assertThat(signal.verdict()).isEqualTo(DriftVerdict.UNDETERMINED);
    assertThat(signal.reasonCode()).isEqualTo(DriftSignal.REASON_INSUFFICIENT_SAMPLE);
    assertThat(signal.overrideRate()).isNull();
  }

  @Test
  void 触阈时需owner复核但不自动改能力状态() {
    DriftSignal signal = DriftSignal.evaluate("M2", 10, 3, new DriftThresholds(0.3, 3), NOW);
    assertThat(signal.verdict()).isEqualTo(DriftVerdict.REVIEW_REQUIRED);
    assertThat(signal.verdict().isActionable()).isTrue();
    assertThat(signal.reasonCode()).isEqualTo(DriftSignal.REASON_THRESHOLD_EXCEEDED);
    assertThat(signal.overrideRate()).isBetween(0.29, 0.31);
    assertThat(signal.claimsModelHealthy()).isFalse();
  }

  @Test
  void 未触阈仅表示无信号不构成模型背书() {
    DriftSignal signal = DriftSignal.evaluate("M2", 10, 1, new DriftThresholds(0.3, 3), NOW);
    assertThat(signal.verdict()).isEqualTo(DriftVerdict.NO_ACTIONABLE_SIGNAL);
    assertThat(signal.reasonCode()).isEqualTo(DriftSignal.REASON_BELOW_THRESHOLD_NOT_ENDORSEMENT);
    assertThat(signal.claimsModelHealthy()).isFalse();
  }

  @Test
  void 边界推翻率等于阈值触阈且全量推翻仍受控() {
    DriftSignal equal = DriftSignal.evaluate("M2", 10, 3, new DriftThresholds(0.3, 3), NOW);
    assertThat(equal.verdict()).isEqualTo(DriftVerdict.REVIEW_REQUIRED);
    DriftSignal all = DriftSignal.evaluate("M2", 10, 10, new DriftThresholds(0.9, 3), NOW);
    assertThat(all.verdict()).isEqualTo(DriftVerdict.REVIEW_REQUIRED);
    assertThat(all.overrideRate()).isEqualTo(1.0);
    assertThat(all.claimsModelHealthy()).isFalse();
  }

  @Test
  void 零样本不可判定且NaN阈值被拒绝() {
    DriftSignal zero = DriftSignal.evaluate("M2", 0, 0, new DriftThresholds(0.3, 3), NOW);
    assertThat(zero.verdict()).isEqualTo(DriftVerdict.UNDETERMINED);
    assertThat(zero.reasonCode()).isEqualTo(DriftSignal.REASON_INSUFFICIENT_SAMPLE);
    assertThatThrownBy(() -> new DriftThresholds(Double.NaN, 3))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 非法阈值与非法计数被拒绝() {
    assertThatThrownBy(() -> new DriftThresholds(0.0, 3))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DriftThresholds(1.1, 3))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DriftThresholds(0.3, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DriftSignal.evaluate("M2", 3, 4, new DriftThresholds(0.3, 3), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
