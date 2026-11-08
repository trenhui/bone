package com.bone.claim.domain.model.adjustment.strategy;

import com.bone.claim.domain.model.adjustment.engine.AdjustmentEngine;
import com.bone.claim.domain.model.adjustment.engine.LiabilityRule.LiabilityForm;
import com.bone.claim.domain.model.adjustment.engine.OccurrenceGrouper;
import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import java.util.List;

/**
 * 定额给付型策略：V1 条款语料未给出「触发条件 / 给付金额表 / 与免赔叠加」的推导规则，故 fail-closed。
 *
 * <p>拒绝而非套用报销比例：套用会产出一份「看起来正常」的赔款，在报表和对账里都查不出来。 未来补齐规则时只改本类，并把内核支持矩阵与本文档口径同批更新。
 */
@SuppressWarnings("deprecation")
public final class FixedAmountAdjustmentStrategy implements AdjustmentStrategy {

  @Override
  public LiabilityForm form() {
    return LiabilityForm.FIXED_AMOUNT;
  }

  @Override
  public AdjustmentEngine.Outcome calculate(
      AdjustmentEngine.Request request, List<OccurrenceGrouper.Fact> facts) {
    throw RuleGap.of(
        RuleGap.Code.LIABILITY_FORM_UNSUPPORTED, "定额给付型责任尚无金额推导规则，禁止按报销型公式计算：须先补齐给付条件与金额表");
  }
}
