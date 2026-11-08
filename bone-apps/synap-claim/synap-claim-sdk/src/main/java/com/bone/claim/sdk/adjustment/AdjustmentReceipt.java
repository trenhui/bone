package com.bone.claim.sdk.adjustment;

import java.math.BigDecimal;
import java.util.List;

/**
 * 纯函数理算出参（SDK 契约）。
 *
 * @param totalAmount 赔付金额（两位小数）
 * @param zeroReason 零赔付归因受控码，非零赔付为空串
 * @param steps 计算链可审计轨迹，顺序即运算顺序
 */
public record AdjustmentReceipt(BigDecimal totalAmount, String zeroReason, List<String> steps) {

  public AdjustmentReceipt {
    steps = List.copyOf(steps);
  }
}
