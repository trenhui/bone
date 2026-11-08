package com.bone.claim.domain.model.adjustment.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 免赔引擎：把 §6 的 6 个维度算成一个"扣多少、剩下多少"的确定性结果。
 *
 * <p><b>本文档最容易写错的一条（I5）</b>：相对免赔的语义是"<b>够了就一分不减</b>"，不是"够了就减掉"。
 * 多数实现会把它写成绝对免赔的同义词，结果是把本该全赔的案子白扣一笔——而且因为金额看起来"更保守"， 复核时很难被发现。故本类把两个分支的四个可能结果（绝对/相对 ×
 * 跨门槛/未跨）逐一显式列出， 不允许走同一条代码路径。
 *
 * <p><b>为什么免赔对象要影响运算顺序</b>（§6.3）：
 *
 * <pre>
 *   对象 = 理算金额（默认）：理算 = 发票费用 × 比例 − 剩余免赔     ← 折后减固定额
 *   对象 = 发票费用        ：理算 = (发票费用 − 剩余免赔) × 比例   ← 减固定额后再打折
 * </pre>
 *
 * <p>两者<b>不等价</b>：发票 1000、比例 80%、免赔 200 时分别是 600 与 640。口径搞错会系统性少赔/超赔，
 * 且金额越大差越大。故本类只负责"算免赔额与分支"，运算顺序由 {@link AdjustmentEngine} 按 {@link DeductibleRule.Target}
 * 编排——顺序不在本类里"顺手"实现，避免两处各写一遍。
 */
public final class DeductibleEngine {

  private DeductibleEngine() {}

  /** 命中分支（受控枚举：拒赔五要素要能说清"为什么没赔"）。 */
  public enum Branch {
    /** 未启用免赔。 */
    NOT_APPLIED,
    /** 可赔金额 ≤ 剩余免赔：金额被免赔吃掉。 */
    WITHIN_DEDUCTIBLE,
    /** 可赔金额 > 剩余免赔：跨过门槛。 */
    EXCEEDED_DEDUCTIBLE
  }

  /**
   * 一次免赔的结果。
   *
   * @param branch 命中分支
   * @param deducted <b>实际消抵的免赔额</b>（相对免赔跳档时为 0——确实一分没减，账面不得虚增）
   * @param payable 扣免赔后的可赔金额
   * @param remainingAfter 剩余免赔（相对免赔跳档后归零：门槛已达成）
   */
  public record Outcome(
      Branch branch, BigDecimal deducted, BigDecimal payable, BigDecimal remainingAfter) {

    public Outcome {
      deducted = Money.nonNegative(deducted);
      payable = Money.nonNegative(payable);
      remainingAfter = Money.nonNegative(remainingAfter);
    }

    /** 是否因免赔导致零赔付（归因用）。 */
    public boolean zeroByDeductible() {
      return payable.signum() == 0 && deducted.signum() > 0;
    }
  }

  /**
   * 按 {@link DeductibleRule.Basis} 解析"本次可用剩余免赔"。
   *
   * <p><b>这里就是"第 2 案重扣满额"的防线</b>：{@code ANNUAL} 必须减去跨案已用（累计键 = 保单 + 主被 + 责任 + 年度， 而不是
   * claim_id）；{@code PER_CLAIM} 才允许每次用满额。
   *
   * @param rule 免赔规则
   * @param accumulatedUsed 跨案已消抵额（读侧按 {@link #effectiveUsed} 折算后传入）
   */
  public static BigDecimal resolveRemaining(DeductibleRule rule, BigDecimal accumulatedUsed) {
    if (rule == null || rule.inactive()) {
      return Money.ZERO;
    }
    BigDecimal configured = rule.amount();
    return switch (rule.basis()) {
      case ANNUAL -> Money.nonNegative(configured.subtract(Money.nz(accumulatedUsed)));
      case PER_CLAIM -> configured;
      case PER_DAY -> throw RuleGap.of(
          RuleGap.Code.DEDUCTIBLE_CONFIG_INVALID, "免赔方式为按天数：费用补偿型（V1）无天数语义，须走津贴型分支");
    };
  }

  /**
   * 按 {@link DeductibleRule.Offset} 把"已用免赔额"折算成引擎口径（§6.6）。
   *
   * <p><b>为什么放在引擎而不是读侧各自折算</b>：三个口径的差别只是"医保/三方赔付算不算填满免赔"。
   * 若由读侧各写一遍，同一条责任在不同入口的免赔已用额会不一致——次案金额因此漂移，且极难复现。 故折算公式在此收口，读侧只负责提供原始累计量。
   *
   * @param accumulatedUsed 历次已消抵的免赔额
   * @param medicalPaid 历次医保（统筹/基金）已赔付
   * @param thirdPartyPaid 历次第三方（商保/公账/其他基金）已赔付
   */
  public static BigDecimal effectiveUsed(
      DeductibleRule rule,
      BigDecimal accumulatedUsed,
      BigDecimal medicalPaid,
      BigDecimal thirdPartyPaid) {
    if (rule == null) {
      return Money.nz(accumulatedUsed);
    }
    BigDecimal used = Money.nz(accumulatedUsed);
    return switch (rule.offset()) {
      case MEDICAL_AND_THIRD_PARTY -> used.add(Money.nz(medicalPaid)).add(Money.nz(thirdPartyPaid));
      case NONE -> used;
      case THIRD_PARTY_ONLY -> used.add(Money.nz(thirdPartyPaid));
    };
  }

  /**
   * 单次免赔（I5 的唯一实现点）。
   *
   * @param rule 免赔规则
   * @param remaining 剩余免赔（已按 {@link #resolveRemaining} 解析）
   * @param payable 免赔对象金额（按 {@link DeductibleRule.Target} 传入：理算金额或发票费用）
   */
  public static Outcome apply(DeductibleRule rule, BigDecimal remaining, BigDecimal payable) {
    BigDecimal rem = Money.nonNegative(remaining);
    BigDecimal due = Money.nonNegative(payable);
    if (rule == null || rule.inactive()) {
      return new Outcome(Branch.NOT_APPLIED, Money.ZERO, due, rem);
    }
    if (rule.kind() == DeductibleRule.Kind.ABSOLUTE) {
      // 绝对免赔：总是扣掉剩余免赔。跨门槛 ⇒ 应赔 = 应赔 − 剩余；未跨 ⇒ 应赔归零。
      if (due.compareTo(rem) > 0) {
        return new Outcome(Branch.EXCEEDED_DEDUCTIBLE, rem, due.subtract(rem), Money.ZERO);
      }
      return new Outcome(Branch.WITHIN_DEDUCTIBLE, due, Money.ZERO, rem.subtract(due));
    }
    // 相对免赔：够了就一分不减（I5）。注意 deducted 记 0 而不是 rem——账面只记"真的减掉的"。
    if (due.compareTo(rem) > 0) {
      return new Outcome(Branch.EXCEEDED_DEDUCTIBLE, Money.ZERO, due, Money.ZERO);
    }
    return new Outcome(Branch.WITHIN_DEDUCTIBLE, due, Money.ZERO, rem.subtract(due));
  }

  /**
   * 责任级免赔（§5.5 示例 A(2)）：各费用类型理算金额<b>求和后</b>再扣一次。
   *
   * <p>这是默认粒度，也是原文唯一用文字明确规定顺序的分支：「∵ 免赔是责任免赔非费用类型免赔， 即费用类型理算金额相加后再扣减免赔」。
   */
  public static Outcome applyLiabilityLevel(
      DeductibleRule rule, BigDecimal remaining, BigDecimal totalPayable) {
    return apply(rule, remaining, totalPayable);
  }

  /**
   * 费用类型级免赔（§5.5 示例 A(3)）：各费用类型<b>各自</b>扣对应免赔。
   *
   * <p>两个细节必须显式，否则同一份配置两种结果：
   *
   * <ol>
   *   <li>额度来源是 {@link DeductibleRule#amountOf}（每类型独立额度），不是共享责任额度池；
   *   <li>返回的 {@code remainingAfter} 取各自扣减后的最小值 —— 该字段只用于跨案回写诊断， 真正的跨案已用额由 {@link
   *       Result#totalDeducted} 累加。
   * </ol>
   *
   * @param payableByBucket 各费用类型的理算金额（顺序即消耗顺序，与配置声明序一致）
   */
  public static Result applyPerBucket(
      DeductibleRule rule, Map<FeePools.Bucket, BigDecimal> payableByBucket) {
    Map<FeePools.Bucket, Outcome> outcomes = new LinkedHashMap<>();
    BigDecimal totalDeducted = Money.ZERO;
    BigDecimal totalPayable = Money.ZERO;
    BigDecimal minRemaining = null;
    for (Map.Entry<FeePools.Bucket, BigDecimal> entry : payableByBucket.entrySet()) {
      FeePools.Bucket bucket = entry.getKey();
      BigDecimal due = Money.nz(entry.getValue());
      BigDecimal bucketLimit = rule == null ? Money.ZERO : rule.amountOf(bucket);
      // 每类型有自己的额度，因此"剩余"就是该类型额度本身；跨案已用按类型无法细分时以额度为上限（见 §6.7〔缺〕）。
      Outcome outcome = apply(rule, bucketLimit, due);
      outcomes.put(bucket, outcome);
      totalDeducted = totalDeducted.add(outcome.deducted());
      totalPayable = totalPayable.add(outcome.payable());
      minRemaining =
          minRemaining == null
              ? outcome.remainingAfter()
              : minRemaining.min(outcome.remainingAfter());
    }
    return new Result(
        outcomes, totalDeducted, totalPayable, minRemaining == null ? Money.ZERO : minRemaining);
  }

  /** 多桶结果汇总。 */
  public record Result(
      Map<FeePools.Bucket, Outcome> outcomes,
      BigDecimal totalDeducted,
      BigDecimal totalPayable,
      BigDecimal remainingAfter) {

    public Result {
      outcomes = Map.copyOf(outcomes);
    }

    /** 各桶分支（诊断/快照用）。 */
    public List<String> branchTrace() {
      List<String> trace = new ArrayList<>();
      outcomes.forEach((bucket, outcome) -> trace.add(bucket + ":" + outcome.branch().name()));
      return trace;
    }
  }
}
