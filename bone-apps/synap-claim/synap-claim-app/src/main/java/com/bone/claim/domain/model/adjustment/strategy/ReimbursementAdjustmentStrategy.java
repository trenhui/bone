package com.bone.claim.domain.model.adjustment.strategy;

import com.bone.claim.domain.model.adjustment.engine.AdjustmentEngine;
import com.bone.claim.domain.model.adjustment.engine.LiabilityRule.LiabilityForm;
import com.bone.claim.domain.model.adjustment.engine.OccurrenceGrouper;
import java.util.List;

/**
 * 费用补偿（报销）型策略：当前唯一具备完整金额语义的形式。
 *
 * <p><b>不重写算法</b>：九步主链仍是唯一实现，策略只做形式分派。分两处写会立刻产生「同一条款两种算法」， 而分叉只在多费用池 / 多次限额同时生效的案子上暴露，是最难复现的一类错赔。
 */
@SuppressWarnings("deprecation")
public final class ReimbursementAdjustmentStrategy implements AdjustmentStrategy {

  @Override
  public LiabilityForm form() {
    return LiabilityForm.REIMBURSEMENT;
  }

  @Override
  public AdjustmentEngine.Outcome calculate(
      AdjustmentEngine.Request request, List<OccurrenceGrouper.Fact> facts) {
    return AdjustmentEngine.calculate(request, facts);
  }
}
