package com.bone.claim.domain.model.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 错误预算判据测试。
 *
 * <p>三条最要紧的判据：空集给 {@code null} 而不是 0、<b>小样本不得判超限</b>（否则灰度第一天就会被唯一一次抽检差异熔断）、 截断必须显式且不得当作「已证明超限」。
 */
class AiRolloutBudgetTest {

  private static final BigDecimal TARGET = new BigDecimal("0.02");
  private static final int MIN_SAMPLES = 30;

  @Test
  @DisplayName("无样本 → errorRate 为 null（不是 0），结论 NO_EVIDENCE，不熔断")
  void noEvidenceGivesNullRate() {
    AiRolloutBudget budget = new AiRolloutBudget(0, 0, false, TARGET, MIN_SAMPLES);

    assertThat(budget.errorRate()).isNull();
    assertThat(budget.remainingRatio()).isNull();
    assertThat(budget.verdict()).isEqualTo(AiRolloutBudget.Verdict.NO_EVIDENCE);
    assertThat(budget.verdict().mustHalt()).isFalse();
    // 证据不足不放行晋升：冷启动可以走，但不能靠「没数据」升档。
    assertThat(budget.verdict().blocksPromotion()).isTrue();
  }

  @Test
  @DisplayName("1 抽检 1 差异（100% > 2%）不得熔断——样本不足时超限没有统计意义")
  void smallSampleNeverHalts() {
    AiRolloutBudget budget = new AiRolloutBudget(1, 1, false, TARGET, MIN_SAMPLES);

    assertThat(budget.errorRate()).isEqualByComparingTo("1.0000");
    assertThat(budget.sampleSufficient()).isFalse();
    assertThat(budget.verdict()).isEqualTo(AiRolloutBudget.Verdict.INSUFFICIENT_SAMPLES);
    assertThat(budget.verdict().mustHalt()).isFalse();
  }

  @Test
  @DisplayName("样本达标且超目标 → OVER_TARGET，必须熔断")
  void overTargetHalts() {
    // 31 条中 1 条差异 = 3.23% > 2%
    AiRolloutBudget budget = new AiRolloutBudget(31, 1, false, TARGET, MIN_SAMPLES);

    assertThat(budget.sampleSufficient()).isTrue();
    assertThat(budget.verdict()).isEqualTo(AiRolloutBudget.Verdict.OVER_TARGET);
    assertThat(budget.verdict().mustHalt()).isTrue();
    assertThat(budget.remainingRatio()).isNegative();
  }

  @Test
  @DisplayName("样本达标且零差异 → WITHIN_BUDGET，不熔断、不阻晋升")
  void withinBudgetDoesNotBlock() {
    AiRolloutBudget budget = new AiRolloutBudget(60, 0, false, TARGET, MIN_SAMPLES);

    assertThat(budget.errorRate()).isEqualByComparingTo("0.0000");
    assertThat(budget.verdict()).isEqualTo(AiRolloutBudget.Verdict.WITHIN_BUDGET);
    assertThat(budget.verdict().mustHalt()).isFalse();
    assertThat(budget.verdict().blocksPromotion()).isFalse();
    assertThat(budget.remainingRatio()).isEqualByComparingTo("1.0000");
  }

  @Test
  @DisplayName("恰好等于目标不算超限（边界含等于，避免 2.00% 被当成 2.01%）")
  void exactlyAtTargetIsWithinBudget() {
    // 100 条中 2 条 = 2.00%
    AiRolloutBudget budget = new AiRolloutBudget(100, 2, false, TARGET, MIN_SAMPLES);

    assertThat(budget.errorRate()).isEqualByComparingTo("0.0200");
    assertThat(budget.verdict()).isEqualTo(AiRolloutBudget.Verdict.WITHIN_BUDGET);
  }

  @Test
  @DisplayName("截断 → EVIDENCE_TRUNCATED：不熔断，但阻止晋升（子集不能当全量结论）")
  void truncatedIsInconclusive() {
    AiRolloutBudget budget = new AiRolloutBudget(500, 40, true, TARGET, MIN_SAMPLES);

    assertThat(budget.verdict()).isEqualTo(AiRolloutBudget.Verdict.EVIDENCE_TRUNCATED);
    assertThat(budget.verdict().mustHalt()).isFalse();
    assertThat(budget.verdict().blocksPromotion()).isTrue();
  }

  @Test
  @DisplayName("差异数多于抽检数 → 构造期即抛（否则会产出 >100% 的错误率）")
  void diffCannotExceedInspected() {
    assertThatThrownBy(() -> new AiRolloutBudget(3, 4, false, TARGET, MIN_SAMPLES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("差异数不得多于抽检数");
  }

  @Test
  @DisplayName("负数计数 → 构造期即抛（上游聚合写错不得静默变成一个比率）")
  void negativeCountsRejected() {
    assertThatThrownBy(() -> new AiRolloutBudget(-1, 0, false, TARGET, MIN_SAMPLES))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
