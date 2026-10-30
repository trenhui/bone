package com.bone.claim.domain.model.adjustment.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 理算金额工具：全引擎唯一的精度入口（§8.3 精度约定）。
 *
 * <p><b>为什么必须收口</b>：金额误差在理算里不是"小数点后多几位"的小事——同一份条款用两种舍入， 一年后对不上账就是资金事故。故中间计算一律 6 位、结果一律 2 位、一律
 * {@link RoundingMode#HALF_UP}，且<b>禁止 double 中转</b>（{@code new BigDecimal(0.1)} 的二进制误差会在乘比例时放大）。
 *
 * <p><b>为什么单独成类而不是散在各处 {@code setScale}</b>：散落调用必然出现"这一处忘了 HALF_UP"（默认 6 位进位到 2 位时 会抛 {@code
 * ArithmeticException}，或静默用 HALF_EVEN）。收口后，精度只有一处可改， 变异测试也只需要打一处就能验证"精度是否真的被断言到"。
 */
public final class Money {

  /** 中间计算精度（§8.3 CALC_SCALE=6）。 */
  public static final int CALC_SCALE = 6;

  /** 结果精度（§8.3 RESULT_SCALE=2）。 */
  public static final int RESULT_SCALE = 2;

  /** 零（标度 2，避免 {@code 0} 与 {@code 0.00} 在字符串快照里漂移）。 */
  public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(RESULT_SCALE);

  /** 百分之百（比例上限，{@link #percentToRate} 的钳制目标）。 */
  public static final BigDecimal ONE = BigDecimal.ONE;

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private Money() {}

  /** 空值归零（{@code items.*} 多列 DEFAULT 0，但投影可能为 null）。 */
  public static BigDecimal nz(BigDecimal value) {
    return value == null ? ZERO : value;
  }

  /** 中间精度（6 位 HALF_UP）。 */
  public static BigDecimal calc(BigDecimal value) {
    return nz(value).setScale(CALC_SCALE, RoundingMode.HALF_UP);
  }

  /** 结果精度（2 位 HALF_UP）。 */
  public static BigDecimal round(BigDecimal value) {
    return nz(value).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * 百分数 → 比例（{@code 90} → {@code 0.900000}）。
   *
   * <p>超 100% 钳到 1.0 而不是抛异常：配置错误不应让"已经核定的案子算不出钱"，但<b>必须可审计</b> —— 调用方据 {@link
   * #isRateOverflow(BigDecimal)} 把钳制事实写进诊断，不做静默钳制。
   */
  public static BigDecimal percentToRate(BigDecimal percent) {
    BigDecimal rate = nz(percent).divide(HUNDRED, CALC_SCALE, RoundingMode.HALF_UP);
    return rate.min(ONE);
  }

  /** 配置比例是否越过 100%（用于把"钳制"这一事实登记进诊断，而非静默）。 */
  public static boolean isRateOverflow(BigDecimal percent) {
    return nz(percent).compareTo(HUNDRED) > 0;
  }

  /** 钳零：赔付/免赔/额度余额一律不得为负（负值只能来自上游计算错误）。 */
  public static BigDecimal nonNegative(BigDecimal value) {
    return value == null || value.signum() < 0 ? ZERO : value;
  }

  /** 求和（空集合返回 0.00）。 */
  public static BigDecimal sum(Iterable<BigDecimal> values) {
    BigDecimal total = ZERO;
    for (BigDecimal value : values) {
      total = total.add(nz(value));
    }
    return total;
  }

  /** 是否为零（按数值比较，不受标度影响）。 */
  public static boolean isZero(BigDecimal value) {
    return nz(value).signum() == 0;
  }

  /**
   * 按权重把 {@code total} 拆成若干份，且 <b>Σ 各份 ≡ total</b>（尾差补齐到权重最大的一份）。
   *
   * <p><b>为什么需要它</b>：责任级免赔落在"多费用池"上时必须分摊到池，否则归因明细加不回总额（违反 I1）。 朴素的 {@code total × wᵢ / Σw}
   * 各份独立取整会出现 ±0.01 的漂移，分位守恒断言随即转红——这不是"小数精度无关紧要"，而是 监管口径要求"明细可加回合计"。故此处显式补齐尾差。
   *
   * <p>权重全零时返回全零（不抛异常：没有可分摊的基数本身就是合法场景）。
   */
  public static List<BigDecimal> proRata(BigDecimal total, List<BigDecimal> weights) {
    int size = weights.size();
    List<BigDecimal> shares = new ArrayList<>(size);
    BigDecimal weightSum = sum(weights);
    BigDecimal target = round(total);
    if (size == 0) {
      return shares;
    }
    if (weightSum.signum() == 0) {
      for (int i = 0; i < size; i++) {
        shares.add(ZERO);
      }
      return shares;
    }
    BigDecimal allocated = ZERO;
    int largest = 0;
    for (int i = 0; i < size; i++) {
      BigDecimal weight = nz(weights.get(i));
      BigDecimal share = target.multiply(weight).divide(weightSum, RESULT_SCALE, RoundingMode.DOWN);
      // 单份不得超过其权重（否则"分摊额 > 基数"会造出负的可赔额）
      share = share.min(round(weight));
      shares.add(share);
      allocated = allocated.add(share);
      if (weight.compareTo(nz(weights.get(largest))) > 0) {
        largest = i;
      }
    }
    BigDecimal residue = target.subtract(allocated);
    if (residue.signum() != 0) {
      BigDecimal patched = shares.get(largest).add(residue);
      shares.set(largest, patched.min(round(weights.get(largest))).max(ZERO));
    }
    return shares;
  }
}
