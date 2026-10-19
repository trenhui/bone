package com.bone.aiorch.domain.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 确定性闸门：置信度 / 金额 / AI 可用性三条熔断线 + 纯规则放行 + 阈值校验。 */
class DecisionGateTest {

  private final DecisionGate gate = new DecisionGate();

  @Test
  void auto_approves_when_confidence_high_and_amount_low() {
    DecisionVerdict verdict = gate.evaluate(0.92, new BigDecimal("1000"), true, true);
    assertThat(verdict.autoApproved()).isTrue();
    assertThat(verdict.requiresHuman()).isFalse();
    assertThat(verdict.reasons()).isEmpty();
  }

  @Test
  void requires_human_when_confidence_below_threshold() {
    DecisionVerdict verdict = gate.evaluate(0.80, new BigDecimal("1000"), true, true);
    assertThat(verdict.requiresHuman()).isTrue();
    assertThat(verdict.reasons()).containsExactly(DecisionVerdict.REASON_CONFIDENCE_LOW);
  }

  @Test
  void requires_human_when_amount_above_threshold() {
    DecisionVerdict verdict = gate.evaluate(0.95, new BigDecimal("5001"), true, true);
    assertThat(verdict.requiresHuman()).isTrue();
    assertThat(verdict.reasons()).containsExactly(DecisionVerdict.REASON_AMOUNT_HIGH);
  }

  @Test
  void amount_equal_to_threshold_is_allowed() {
    // 阈值语义是「超过」才转人工，等于仍自动（避免边界歧义）
    assertThat(gate.evaluate(0.95, new BigDecimal("5000"), true, true).autoApproved()).isTrue();
  }

  @Test
  void combines_both_reasons() {
    DecisionVerdict verdict = gate.evaluate(0.5, new BigDecimal("9000"), true, true);
    assertThat(verdict.reasons())
        .containsExactly(DecisionVerdict.REASON_CONFIDENCE_LOW, DecisionVerdict.REASON_AMOUNT_HIGH);
  }

  @Test
  void requires_human_when_ai_unavailable() {
    DecisionVerdict verdict = gate.evaluate(0.99, new BigDecimal("10"), false, true);
    assertThat(verdict.requiresHuman()).isTrue();
    assertThat(verdict.reasons()).containsExactly(DecisionVerdict.REASON_AI_UNAVAILABLE);
  }

  @Test
  void pure_rule_decision_auto_approves_without_ai() {
    // 无 LLM 步骤 = 纯规则决策，规则本身确定性，不需要 AI 闸门
    assertThat(gate.evaluate(null, new BigDecimal("999999"), true, false).autoApproved()).isTrue();
  }

  @Test
  void null_confidence_requires_human_when_llm_step() {
    // 声明 LLM 步骤却无模型置信度：合规 fail-closed 强制人工，禁止静默自动放行
    DecisionVerdict lowAmount = gate.evaluate(null, new BigDecimal("1000"), true, true);
    assertThat(lowAmount.requiresHuman()).isTrue();
    assertThat(lowAmount.reasons()).containsExactly(DecisionVerdict.REASON_CONFIDENCE_MISSING);
    // 金额高时仍转人工（叠加金额原因由金额分支处理，此处仅验证不自动放行）
    assertThat(gate.evaluate(null, new BigDecimal("8000"), true, true).requiresHuman()).isTrue();
  }

  @Test
  void null_amount_skips_amount_gate() {
    assertThat(gate.evaluate(0.95, null, true, true).autoApproved()).isTrue();
  }

  @Test
  void rejects_invalid_thresholds() {
    assertThatThrownBy(() -> new DecisionGate(0, BigDecimal.TEN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DecisionGate(1.5, BigDecimal.TEN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DecisionGate(0.9, new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
