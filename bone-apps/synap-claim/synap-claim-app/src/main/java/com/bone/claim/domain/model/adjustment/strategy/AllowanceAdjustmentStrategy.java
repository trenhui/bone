package com.bone.claim.domain.model.adjustment.strategy;

import com.bone.claim.domain.model.adjustment.engine.AdjustmentEngine;
import com.bone.claim.domain.model.adjustment.engine.LiabilityRule.LiabilityForm;
import com.bone.claim.domain.model.adjustment.engine.OccurrenceGrouper;
import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import java.util.List;

/** 津贴给付型策略：V1 条款语料未给出「按天 / 按次 / 与免赔叠加」的推导规则，故 fail-closed。 */
@SuppressWarnings("deprecation")
public final class AllowanceAdjustmentStrategy implements AdjustmentStrategy {

  @Override
  public LiabilityForm form() {
    return LiabilityForm.ALLOWANCE;
  }

  @Override
  public AdjustmentEngine.Outcome calculate(
      AdjustmentEngine.Request request, List<OccurrenceGrouper.Fact> facts) {
    throw RuleGap.of(
        RuleGap.Code.LIABILITY_FORM_UNSUPPORTED, "津贴给付型责任尚无金额推导规则，禁止按报销型公式计算：须先补齐给付天数与日额口径");
  }
}
