package com.bone.claim.domain.model.adjustment.engine;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 金额半开区间 {@code [from, to)}（V1 §5.3 区间控件语义）。
 *
 * <p><b>为什么必须是半开</b>：赔付比例的"赔案累计区间"是相邻区间 {@code [0,1000)}、{@code [1000,2000)}、 {@code
 * [2000,10000)}。若写成闭区间，金额恰为 1000 时两个区间都命中，"定位到唯一适用的比例"就不成立； 引擎将不得不在两格之间二选一——那正是同一张发票两种算法的来源。
 *
 * <p>{@code to == null} 表示无上界（{@code [from, +∞)}），对应配置里"止"留空的最后一档。
 */
public record AmountRange(BigDecimal from, BigDecimal to) {

  /** 上界为空的排序哨兵（用于 {@link #hasOverlap} 的比较，不参与业务计算）。 */
  private static final BigDecimal UNBOUNDED = new BigDecimal("9E18");

  public AmountRange {
    if (from == null || from.signum() < 0) {
      throw new IllegalArgumentException("区间左界不得为空或负数");
    }
    if (to != null && to.compareTo(from) <= 0) {
      throw new IllegalArgumentException("区间右界必须大于左界：[%s, %s)".formatted(from, to));
    }
  }

  /** 任意金额（不限区间）：{@code [0, +∞)}。 */
  public static AmountRange any() {
    return new AmountRange(BigDecimal.ZERO, null);
  }

  /** {@code [from, +∞)}。 */
  public static AmountRange atLeast(String from) {
    return new AmountRange(new BigDecimal(from), null);
  }

  /** {@code [from, to)}。 */
  public static AmountRange of(String from, String to) {
    return new AmountRange(new BigDecimal(from), to == null ? null : new BigDecimal(to));
  }

  /** 是否命中（左闭右开）。 */
  public boolean contains(BigDecimal amount) {
    BigDecimal value = Money.nz(amount);
    if (value.compareTo(from) < 0) {
      return false;
    }
    return to == null || value.compareTo(to) < 0;
  }

  /** 是否无上界。 */
  public boolean unbounded() {
    return to == null;
  }

  /**
   * 区间集合是否两两不重叠（I3：任一金额恰好命中一个比例档）。
   *
   * <p>只做"相邻不重叠"判定即可：区间按左界排序后，前一档的右界必须 ≤ 后一档的左界。 若配置出现重叠，引擎在命中两格时无法选择，必须在配置装载期拒绝，而不是在理赔时随机挑一格。
   */
  public static boolean pairwiseDisjoint(List<AmountRange> ranges) {
    if (ranges.size() < 2) {
      return true;
    }
    List<AmountRange> sorted =
        ranges.stream().sorted(Comparator.comparing(AmountRange::from)).toList();
    for (int i = 1; i < sorted.size(); i++) {
      BigDecimal previousTo = sorted.get(i - 1).to == null ? UNBOUNDED : sorted.get(i - 1).to;
      if (previousTo.compareTo(sorted.get(i).from) > 0) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return "[" + from.toPlainString() + "," + (to == null ? "∞" : to.toPlainString()) + ")";
  }
}
