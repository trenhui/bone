package com.bone.claim.domain.model.mapping.stp;

import java.util.List;

/**
 * STP 闸门纯评估器（无仓储依赖，可单测 + 变异验证）。
 *
 * <p>把「映射质量」翻译成「业务风险开关」（主设计 §6.9）：五把尺任一不达标，该 (保司×域) 赔案强制转人工 / 禁自动理算。
 *
 * <p><b>变异验证友好</b>：每把尺是独立布尔分支，逻辑集中于此。删掉任一把尺的判定， 对应「仅该尺不达标」的用例会由红转绿 ⇒ 证明该尺有真实判别力（呼应 §15 双向判据：删实现转红 +
 * 反向对照也要红）。
 */
public final class StpGateEvaluator {

  private StpGateEvaluator() {}

  public static StpGateVerdict evaluate(StpMetrics m) {
    boolean itemOk = m.itemHitRate() >= StpThresholds.ITEM_HIT_RATE_MIN;
    boolean caseOk = m.caseStraightThroughRate() >= StpThresholds.CASE_STRAIGHT_THROUGH_RATE_MIN;
    boolean openOk = m.openMissCount() <= StpThresholds.OPEN_MISS_MAX;
    boolean correctionOk = m.correctionEvents7d() <= StpThresholds.CORRECTION_EVENTS_7D_MAX;
    boolean conflictOk = m.conflictCount() <= StpThresholds.CONFLICT_MAX;

    List<StpGateVerdict.ConditionResult> results =
        List.of(
            new StpGateVerdict.ConditionResult(
                StpCondition.ITEM_HIT_RATE,
                itemOk,
                "命中率="
                    + round(m.itemHitRate())
                    + "% (门槛≥"
                    + StpThresholds.ITEM_HIT_RATE_MIN
                    + "%)"),
            new StpGateVerdict.ConditionResult(
                StpCondition.CASE_STRAIGHT_THROUGH_RATE,
                caseOk,
                "直通率="
                    + round(m.caseStraightThroughRate())
                    + "% (门槛≥"
                    + StpThresholds.CASE_STRAIGHT_THROUGH_RATE_MIN
                    + "%)"),
            new StpGateVerdict.ConditionResult(
                StpCondition.OPEN_MISS,
                openOk,
                "OPEN未命中=" + m.openMissCount() + " (门槛≤" + StpThresholds.OPEN_MISS_MAX + ")"),
            new StpGateVerdict.ConditionResult(
                StpCondition.CORRECTION_EVENTS_7D,
                correctionOk,
                "近7天纠错="
                    + m.correctionEvents7d()
                    + " (门槛≤"
                    + StpThresholds.CORRECTION_EVENTS_7D_MAX
                    + ")"),
            new StpGateVerdict.ConditionResult(
                StpCondition.CONFLICT,
                conflictOk,
                "冲突数=" + m.conflictCount() + " (门槛≤" + StpThresholds.CONFLICT_MAX + ")"));

    boolean allow = itemOk && caseOk && openOk && correctionOk && conflictOk;
    return allow ? StpGateVerdict.allow(results) : StpGateVerdict.block(results);
  }

  private static double round(double v) {
    return Math.round(v * 10.0) / 10.0;
  }
}
