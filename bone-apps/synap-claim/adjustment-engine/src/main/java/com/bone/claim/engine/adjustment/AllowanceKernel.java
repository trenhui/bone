package com.bone.claim.engine.adjustment;

import com.bone.claim.sdk.adjustment.AdjustmentCommand;
import com.bone.claim.sdk.adjustment.AdjustmentForm;
import com.bone.claim.sdk.adjustment.AdjustmentReceipt;
import com.bone.claim.sdk.adjustment.AdjustmentStrategy;
import com.bone.claim.sdk.adjustment.UnsupportedAdjustmentFormException;

/** 津贴给付型内核：V1 语料未给出「按天数 / 按住院次数 / 与免赔如何叠加」的推导规则，故 fail-closed。 */
public final class AllowanceKernel implements AdjustmentStrategy {

  @Override
  public AdjustmentForm form() {
    return AdjustmentForm.ALLOWANCE;
  }

  @Override
  public AdjustmentReceipt calculate(AdjustmentCommand command) {
    throw new UnsupportedAdjustmentFormException(form());
  }
}
