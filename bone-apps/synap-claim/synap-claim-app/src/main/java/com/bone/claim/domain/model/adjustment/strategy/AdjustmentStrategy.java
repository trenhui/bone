package com.bone.claim.domain.model.adjustment.strategy;

import com.bone.claim.domain.model.adjustment.engine.AdjustmentEngine;
import com.bone.claim.domain.model.adjustment.engine.LiabilityRule.LiabilityForm;
import com.bone.claim.domain.model.adjustment.engine.OccurrenceGrouper;
import java.util.List;

/**
 * 领域层理算策略端口：一种责任形式对应一份金额算法。
 *
 * <p>与 SDK 的 {@code com.bone.claim.sdk.adjustment.AdjustmentStrategy} 分工：<b>SDK 端口</b>面向纯函数内核
 * （入参是已归集的标量），<b>本端口</b>面向领域主链（入参是费用明细投影与跨案累计）。两者同名不同层， 不得互相替代：把明细归集塞进内核会让纯函数重新背上领域模型。
 */
public interface AdjustmentStrategy {

  LiabilityForm form();

  @SuppressWarnings("deprecation")
  AdjustmentEngine.Outcome calculate(
      AdjustmentEngine.Request request, List<OccurrenceGrouper.Fact> facts);
}
