package com.bone.claim.sdk.adjustment;

/** 理算策略端口（SDK 契约）：一种责任形式对应一份确定性算法。 */
public interface AdjustmentStrategy {

  AdjustmentForm form();

  AdjustmentReceipt calculate(AdjustmentCommand command);
}
