package com.bone.claim.sdk.adjustment;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 纯函数理算入参（SDK 契约）。
 *
 * <p>只承载「已归集完成的可赔基数」，不承载费用明细、租户、聚合根或任何持久化概念： 费用池归集属于领域层，纯函数内核不重复实现一次。
 *
 * @param form 责任形式
 * @param baseAmount 可赔基数（社保范围筛选后的合计，非负）
 * @param deductible 免赔额（作用于基数，非负）
 * @param payPercent 赔付比例（百分数，如 {@code 80} 表示 80%）
 * @param limit 责任限额（{@code 0} 表示不封顶）
 */
public record AdjustmentCommand(
    AdjustmentForm form,
    BigDecimal baseAmount,
    BigDecimal deductible,
    BigDecimal payPercent,
    BigDecimal limit) {

  public AdjustmentCommand {
    Objects.requireNonNull(form, "责任形式不能为空");
    baseAmount = nonNegative(baseAmount);
    deductible = nonNegative(deductible);
    payPercent = nonNegative(payPercent);
    limit = nonNegative(limit);
  }

  private static BigDecimal nonNegative(BigDecimal value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    if (value.signum() < 0) {
      throw new IllegalArgumentException("金额与比例不得为负：" + value);
    }
    return value;
  }
}
