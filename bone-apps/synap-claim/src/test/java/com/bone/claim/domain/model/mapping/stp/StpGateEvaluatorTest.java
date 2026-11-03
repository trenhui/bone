package com.bone.claim.domain.model.mapping.stp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** STP 评估器纯单测（无仓储依赖）。每把尺独立判定 —— 删掉任一把尺的判定，对应「仅该尺不达标」用例会由红转绿， 即变异验证：证明每把尺都有真实判别力（呼应 §15 双向判据）。 */
class StpGateEvaluatorTest {

  private static StpMetrics metrics(
      double itemHit, double caseStp, long open, long correction, long conflict) {
    return new StpMetrics(1L, "YCBX", "HOSPITAL", itemHit, caseStp, open, correction, conflict);
  }

  private static StpMetrics healthy() {
    return metrics(100.0, 100.0, 0, 0, 0);
  }

  @Test
  @DisplayName("五把尺全达标 → 允许自动理算")
  void allPassAllowsAuto() {
    StpGateVerdict v = StpGateEvaluator.evaluate(healthy());
    assertThat(v.allowAuto()).isTrue();
    assertThat(v.conditions()).allMatch(StpGateVerdict.ConditionResult::passed);
  }

  @Test
  @DisplayName("明细级命中率 99.0% (<99.5%) → 阻断，且仅该条件不达标")
  void itemHitRateBelowThresholdBlocks() {
    StpGateVerdict v = StpGateEvaluator.evaluate(metrics(99.0, 100.0, 0, 0, 0));
    assertThat(v.allowAuto()).isFalse();
    assertOnlyFails(v, StpCondition.ITEM_HIT_RATE);
  }

  @Test
  @DisplayName("赔案级直通率 94% (<95%) → 阻断，且仅该条件不达标")
  void caseStraightThroughBelowThresholdBlocks() {
    StpGateVerdict v = StpGateEvaluator.evaluate(metrics(100.0, 94.0, 0, 0, 0));
    assertThat(v.allowAuto()).isFalse();
    assertOnlyFails(v, StpCondition.CASE_STRAIGHT_THROUGH_RATE);
  }

  @Test
  @DisplayName("OPEN 未命中 = 1（>0）→ 阻断，且仅该条件不达标")
  void openMissBlocks() {
    StpGateVerdict v = StpGateEvaluator.evaluate(metrics(100.0, 100.0, 1, 0, 0));
    assertThat(v.allowAuto()).isFalse();
    assertOnlyFails(v, StpCondition.OPEN_MISS);
  }

  @Test
  @DisplayName("近 7 天纠错(reopen) = 1（>0）→ 阻断，且仅该条件不达标")
  void correctionEventsBlock() {
    StpGateVerdict v = StpGateEvaluator.evaluate(metrics(100.0, 100.0, 0, 1, 0));
    assertThat(v.allowAuto()).isFalse();
    assertOnlyFails(v, StpCondition.CORRECTION_EVENTS_7D);
  }

  @Test
  @DisplayName("冲突数 = 1（>0）→ 阻断，且仅该条件不达标")
  void conflictBlocks() {
    StpGateVerdict v = StpGateEvaluator.evaluate(metrics(100.0, 100.0, 0, 0, 1));
    assertThat(v.allowAuto()).isFalse();
    assertOnlyFails(v, StpCondition.CONFLICT);
  }

  @Test
  @DisplayName("边界值：命中率恰为 99.5% 与直通率恰为 95.0% 视为达标（门槛含等）")
  void boundaryValuesAllowed() {
    assertThat(StpGateEvaluator.evaluate(metrics(99.5, 95.0, 0, 0, 0)).allowAuto()).isTrue();
  }

  private void assertOnlyFails(StpGateVerdict v, StpCondition failed) {
    for (StpGateVerdict.ConditionResult r : v.conditions()) {
      if (r.condition() == failed) {
        assertThat(r.passed()).as(r.condition() + " 必须不达标").isFalse();
      } else {
        assertThat(r.passed()).as(r.condition() + " 必须达标").isTrue();
      }
    }
  }
}
