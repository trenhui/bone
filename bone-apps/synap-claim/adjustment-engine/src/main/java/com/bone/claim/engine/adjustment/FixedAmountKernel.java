package com.bone.claim.engine.adjustment;

import com.bone.claim.sdk.adjustment.AdjustmentCommand;
import com.bone.claim.sdk.adjustment.AdjustmentForm;
import com.bone.claim.sdk.adjustment.AdjustmentReceipt;
import com.bone.claim.sdk.adjustment.AdjustmentStrategy;
import com.bone.claim.sdk.adjustment.UnsupportedAdjustmentFormException;

/**
 * 定额给付型内核：V1 语料未给出「按什么条件触发、按哪版金额表给付」的推导规则，故 fail-closed。
 *
 * <p>保留独立实现类而非共用占位，是为了让「枚举分派」有真实的三类落点，且未来补规则时只需改本类。
 */
public final class FixedAmountKernel implements AdjustmentStrategy {

  @Override
  public AdjustmentForm form() {
    return AdjustmentForm.FIXED_AMOUNT;
  }

  @Override
  public AdjustmentReceipt calculate(AdjustmentCommand command) {
    throw new UnsupportedAdjustmentFormException(form());
  }
}
