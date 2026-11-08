package com.bone.claim.engine.adjustment;

import com.bone.claim.sdk.adjustment.AdjustmentCommand;
import com.bone.claim.sdk.adjustment.AdjustmentForm;
import com.bone.claim.sdk.adjustment.AdjustmentReceipt;
import com.bone.claim.sdk.adjustment.AdjustmentStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 报销型纯函数内核：基数 → 免赔 → 比例 → 限额 → 两位取整。
 *
 * <p><b>零依赖、零状态、零 IO</b>：仅 JDK + SDK 契约，同输入恒同输出，可被 app 用作独立参考轨。 链序与精度（中间 6 位 / 结果 2 位 /
 * HALF_UP、比例按百分数除以 100 后量化 6 位）来自 §8.3 规格， 与双轨 oracle 一致；改动链序会造成系统性少赔或超赔，故每一步都留可审计轨迹。
 */
public final class ReimbursementKernel implements AdjustmentStrategy {

  private static final BigDecimal CENT = new BigDecimal("0.01");
  private static final int CALC_SCALE = 6;
  private static final int RESULT_SCALE = 2;

  @Override
  public AdjustmentForm form() {
    return AdjustmentForm.REIMBURSEMENT;
  }

  @Override
  public AdjustmentReceipt calculate(AdjustmentCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("理算入参为空");
    }
    if (command.form() != AdjustmentForm.REIMBURSEMENT) {
      throw new IllegalArgumentException("报销型内核不处理责任形式 " + command.form());
    }

    BigDecimal base = command.baseAmount();
    BigDecimal deductible = command.deductible().min(base);
    BigDecimal net = base.subtract(deductible).max(BigDecimal.ZERO);
    BigDecimal rate =
        command.payPercent().multiply(CENT).setScale(CALC_SCALE, RoundingMode.HALF_UP);
    BigDecimal gross = net.multiply(rate).setScale(CALC_SCALE, RoundingMode.HALF_UP);
    BigDecimal payable = command.limit().signum() > 0 ? gross.min(command.limit()) : gross;
    BigDecimal total = payable.setScale(RESULT_SCALE, RoundingMode.HALF_UP);

    List<String> steps = new ArrayList<>();
    steps.add("BASE:" + base.toPlainString());
    steps.add("DEDUCT:" + deductible.toPlainString());
    steps.add("RATE:" + rate.toPlainString());
    steps.add("GROSS:" + gross.toPlainString());
    if (command.limit().signum() > 0) {
      steps.add("LIMIT:" + command.limit().toPlainString());
    }
    steps.add("TOTAL:" + total.toPlainString());

    return new AdjustmentReceipt(total, zeroReason(base, deductible, rate, gross, total), steps);
  }

  private static String zeroReason(
      BigDecimal base, BigDecimal deductible, BigDecimal rate, BigDecimal gross, BigDecimal total) {
    if (total.signum() != 0) {
      return "";
    }
    if (base.signum() == 0) {
      return "ZERO_REASONABLE";
    }
    if (deductible.signum() > 0 && base.subtract(deductible).signum() <= 0) {
      return "ZERO_DEDUCT";
    }
    if (rate.signum() == 0 || gross.signum() == 0) {
      return "ZERO_PERCENT";
    }
    return "ZERO_LIMIT";
  }
}
