package com.bone.claim.domain.model.config;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 自动通过错误预算快照（G3-2 的 SLI ①）。
 *
 * <p><b>为什么错误率的分母是「已抽检样本」而不是「全部自动通过案件」</b>：一行代码算出来的错误率若用「全部自动通过数」 当分母，会把 5% 抽样得到的差错数摊到 100%
 * 的分母上，得出一个永远很漂亮的数字——它的变化主要取决于<b>抽样比例</b>， 而不是质量。用「已抽检数」当分母，才是在回答「抽到的那批里错了多少」，也才能与质检员看到的事实对齐。
 * 代价是它测的是<b>抽检口径</b>而非全量真值，这一点必须在端点文案里写明，不能让人误读成全量准确率。
 *
 * <p><b>三条判据都为了防「看起来在管，实际管不住」</b>：
 *
 * <ul>
 *   <li><b>空集给 {@code null} 不给 0</b>：{@code 0%} 是「抽了但一处没差」，{@code null} 是「还没抽过」。报表把 null 静默变 0
 *       会让「冷启动期」看起来像「质量完美」；
 *   <li><b>样本不足不得判超限</b>：{@code 1/1 = 100%} 在数学上超过 2% 的目标，但一次抽样不足以说明任何事。若不设样本量下界，
 *       灰度第一天就会因为唯一一次抽检有差异而自动熔断——熔断器变成随机数发生器；
 *   <li><b>截断必须显式</b>：查询打满上界说明「窗口内样本比取到的多」，此时错误率只代表被取回的那部分。截断后仍当作有效结论， 等于用一个有偏子集决定全量是否继续放款。
 * </ul>
 *
 * <p><b>截断与样本不足都<b>不</b>熔断</b>：熔断是「已经证明有问题」的动作，证据不足只是「还证明不了」。但二者都会 阻止<b>晋升</b>（见 {@link
 * Verdict#blocksPromotion()}）——冷启动必须能走通，缺口也不能被静默。
 */
public record AiRolloutBudget(
    int inspectedCount,
    int diffCount,
    boolean truncated,
    BigDecimal errorRateTarget,
    int minInspectedSamples) {

  /** 判据结论（顺序即优先级：先判「证据能不能用」，再判「用得起的结果是否超限」）。 */
  public enum Verdict {
    /** 查询打满上界：样本是被截断的子集，结论不可用。 */
    EVIDENCE_TRUNCATED,
    /** 窗口内没有任何已抽检样本（冷启动）。 */
    NO_EVIDENCE,
    /** 有样本但少于最小样本量：统计上不足以判断。 */
    INSUFFICIENT_SAMPLES,
    /** 证据充分且错误率超目标 ⇒ 熔断。 */
    OVER_TARGET,
    /** 证据充分且错误率在目标内。 */
    WITHIN_BUDGET;

    /** 是否必须立即停止自动放款（只有「已证明超限」才停）。 */
    public boolean mustHalt() {
      return this == OVER_TARGET;
    }

    /** 是否阻止晋升到更高档（证据不可用或未达标都不许晋升）。 */
    public boolean blocksPromotion() {
      return this != WITHIN_BUDGET;
    }
  }

  public AiRolloutBudget {
    if (inspectedCount < 0 || diffCount < 0) {
      throw new IllegalArgumentException(
          "抽检计数不得为负：inspected=" + inspectedCount + ", diff=" + diffCount);
    }
    if (diffCount > inspectedCount) {
      // 差异数不可能多于抽检数；出现即上游聚合写错，必须炸在构造期而不是产出一个 >100% 的错误率。
      throw new IllegalArgumentException(
          "差异数不得多于抽检数：inspected=" + inspectedCount + ", diff=" + diffCount);
    }
    if (errorRateTarget == null || minInspectedSamples < 1) {
      throw new IllegalArgumentException(
          "预算口径不完整：errorRateTarget="
              + errorRateTarget
              + ", minInspectedSamples="
              + minInspectedSamples);
    }
  }

  /**
   * 抽检口径错误率（0-1）。
   *
   * @return 无样本时返回 {@code null}（不是 0）——「没抽过」与「抽了没错」是两件事
   */
  public BigDecimal errorRate() {
    if (inspectedCount == 0) {
      return null;
    }
    return BigDecimal.valueOf(diffCount)
        .divide(BigDecimal.valueOf(inspectedCount), 4, RoundingMode.HALF_UP);
  }

  /** 样本量是否达到判定门槛。 */
  public boolean sampleSufficient() {
    return inspectedCount >= minInspectedSamples;
  }

  /** 判据结论。 */
  public Verdict verdict() {
    if (truncated) {
      return Verdict.EVIDENCE_TRUNCATED;
    }
    if (inspectedCount == 0) {
      return Verdict.NO_EVIDENCE;
    }
    if (!sampleSufficient()) {
      return Verdict.INSUFFICIENT_SAMPLES;
    }
    BigDecimal rate = errorRate();
    return rate != null && rate.compareTo(errorRateTarget) > 0
        ? Verdict.OVER_TARGET
        : Verdict.WITHIN_BUDGET;
  }

  /**
   * 剩余错误预算（1 减去已用比例）。
   *
   * @return 无样本时 {@code null}；超限时为负值（保留符号，让人一眼看出透支多少）
   */
  public BigDecimal remainingRatio() {
    BigDecimal rate = errorRate();
    if (rate == null) {
      return null;
    }
    return BigDecimal.ONE.subtract(rate.divide(errorRateTarget, 4, RoundingMode.HALF_UP));
  }
}
