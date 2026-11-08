package com.bone.claim.domain.model.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A3 分诊单测（fail-safe / 双阈值独立 / 自动通过安全前置）。
 *
 * <p>关键不变量：AI 只产建议，结论由确定性策略算；任一不确定即转人工，绝不默认通过。
 */
class DecisionTriageTest {

  private static DecisionTriage.Input base() {
    return new DecisionTriage.Input(
        true,
        true,
        false,
        new BigDecimal("300.00"),
        new BigDecimal("0.92"),
        new BigDecimal("0.95"),
        false,
        true,
        false);
  }

  @Test
  @DisplayName("全闸门通过 → AUTO_APPROVE")
  void autoApprove() {
    DecisionTriage.Outcome out = DecisionTriage.evaluate(base());
    assertThat(out.result()).isEqualTo(DecisionResult.AUTO_APPROVE);
    assertThat(out.tier()).isEqualTo(DecisionTier.AUTO);
  }

  @Test
  @DisplayName("证据未齐 → NEED_MORE_EVIDENCE")
  void needMoreEvidence() {
    DecisionTriage.Outcome out =
        DecisionTriage.evaluate(
            new DecisionTriage.Input(
                false,
                true,
                false,
                new BigDecimal("300.00"),
                new BigDecimal("0.92"),
                new BigDecimal("0.95"),
                false,
                true,
                false));
    assertThat(out.result()).isEqualTo(DecisionResult.NEED_MORE_EVIDENCE);
  }

  @Test
  @DisplayName("AI 置信度 <0.7 → 强制人工")
  void aiConfidenceLow() {
    DecisionTriage.Outcome out =
        DecisionTriage.evaluate(
            new DecisionTriage.Input(
                true,
                true,
                false,
                new BigDecimal("300.00"),
                new BigDecimal("0.69"),
                new BigDecimal("0.95"),
                false,
                true,
                false));
    assertThat(out.result()).isEqualTo(DecisionResult.MANUAL_REQUIRED);
    assertThat(out.tier()).isEqualTo(DecisionTier.MANUAL);
    assertThat(out.reason()).isEqualTo("AI_CONFIDENCE_LOW");
  }

  @Test
  @DisplayName("AI 置信度 0.7–0.85 → 复核档（不自动通过）")
  void aiConfidenceReviewTier() {
    DecisionTriage.Outcome out =
        DecisionTriage.evaluate(
            new DecisionTriage.Input(
                true,
                true,
                false,
                new BigDecimal("300.00"),
                new BigDecimal("0.80"),
                new BigDecimal("0.95"),
                false,
                true,
                false));
    assertThat(out.result()).isEqualTo(DecisionResult.MANUAL_REQUIRED);
    assertThat(out.tier()).isEqualTo(DecisionTier.REVIEW);
  }

  @Test
  @DisplayName("双阈值独立：AI 0.92 但 OCR 0.65 → 仍强制人工")
  void ocrThresholdIndependent() {
    DecisionTriage.Outcome out =
        DecisionTriage.evaluate(
            new DecisionTriage.Input(
                true,
                true,
                false,
                new BigDecimal("300.00"),
                new BigDecimal("0.92"),
                new BigDecimal("0.65"),
                false,
                true,
                false));
    assertThat(out.result()).isEqualTo(DecisionResult.MANUAL_REQUIRED);
    assertThat(out.reason()).isEqualTo("OCR_CONFIDENCE_LOW");
  }

  @Test
  @DisplayName("金额 ≥1 万 → 复核档（不自动通过）")
  void amountOverLimit() {
    DecisionTriage.Outcome out =
        DecisionTriage.evaluate(
            new DecisionTriage.Input(
                true,
                true,
                false,
                new BigDecimal("10000.00"),
                new BigDecimal("0.95"),
                new BigDecimal("0.95"),
                false,
                true,
                false));
    assertThat(out.result()).isEqualTo(DecisionResult.MANUAL_REQUIRED);
    assertThat(out.tier()).isEqualTo(DecisionTier.REVIEW);
  }

  @Test
  @DisplayName("反欺诈硬闸未通过 → 强制人工（绝不跳过风控）")
  void fraudGateNotPassed() {
    DecisionTriage.Outcome out =
        DecisionTriage.evaluate(
            new DecisionTriage.Input(
                true,
                true,
                false,
                new BigDecimal("300.00"),
                new BigDecimal("0.95"),
                new BigDecimal("0.95"),
                false,
                false,
                false));
    assertThat(out.result()).isEqualTo(DecisionResult.MANUAL_REQUIRED);
    assertThat(out.reason()).isEqualTo("FRAUD_GATE_NOT_PASSED");
  }

  @Test
  @DisplayName("风险命中 → 强制人工")
  void riskHit() {
    DecisionTriage.Outcome out =
        DecisionTriage.evaluate(
            new DecisionTriage.Input(
                true,
                true,
                false,
                new BigDecimal("300.00"),
                new BigDecimal("0.95"),
                new BigDecimal("0.95"),
                true,
                true,
                false));
    assertThat(out.reason()).isEqualTo("RISK_HIT");
  }

  @Test
  @DisplayName("无置信度（LLM 降级且无规则置信度）→ 强制人工（fail-safe）")
  void confidenceUnknown() {
    DecisionTriage.Outcome out =
        DecisionTriage.evaluate(
            new DecisionTriage.Input(
                true,
                true,
                false,
                new BigDecimal("300.00"),
                null,
                new BigDecimal("0.95"),
                false,
                true,
                false));
    assertThat(out.result()).isEqualTo(DecisionResult.MANUAL_REQUIRED);
    assertThat(out.reason()).isEqualTo("CONFIDENCE_UNKNOWN");
  }

  @Test
  @DisplayName("系统建议拒赔 → SUGGEST_REJECT（待人工确认）")
  void suggestReject() {
    DecisionTriage.Outcome out =
        DecisionTriage.evaluate(
            new DecisionTriage.Input(
                true,
                true,
                false,
                new BigDecimal("300.00"),
                new BigDecimal("0.95"),
                new BigDecimal("0.95"),
                false,
                true,
                true));
    assertThat(out.result()).isEqualTo(DecisionResult.SUGGEST_REJECT);
  }

  @Test
  @DisplayName("规则冲突无法裁决 → 强制人工")
  void ruleConflict() {
    DecisionTriage.Outcome out =
        DecisionTriage.evaluate(
            new DecisionTriage.Input(
                true,
                true,
                true,
                new BigDecimal("300.00"),
                new BigDecimal("0.95"),
                new BigDecimal("0.95"),
                false,
                true,
                false));
    assertThat(out.reason()).isEqualTo("RULE_CONFLICT");
  }
}
