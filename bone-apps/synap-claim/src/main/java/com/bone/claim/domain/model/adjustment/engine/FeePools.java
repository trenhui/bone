package com.bone.claim.domain.model.adjustment.engine;

import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 费用池：把赔案明细归集成"承担费用类型 × 医保内外"的可赔基数（V1 §3.5 / §3.6 / §4.2 步 6）。
 *
 * <p><b>为什么不是"一个 base 数字"</b>：旧内核把全部合理费用加成一个标量，这在单责任单比例下碰巧对； 一旦"合理金额 90% / 自付二 80% / 总自费
 * 70%"，同一张发票的三个池比例不同， 标量模型连"正确"的输入都表达不出来。V1 的理算单元因此必须是<b>分池</b>的：
 *
 * <ul>
 *   <li>{@code REASONABLE} 合理金额（企补口径 ①：已扣基金支付、总自费、自付二、三方已赔、不合理）；
 *   <li>{@code SELF_PAY_2} 自付二（乙类先行自付 + 乙类自费）；
 *   <li>{@code TOTAL_SELF_PAY} 总自费（丙类 + 超限价）；
 *   <li>{@code CUSTOM} 自定义费用类型（原文明确"还包括自定义新增费用类型"，故留扩展槽而非硬编码三段）。
 * </ul>
 *
 * <p><b>为什么每池还要拆医保内外</b>：§4.3 的精确式是"范围内与范围外各自 min 后求和，再用责任额度截断"。
 * 若不分内外，某一类额度用尽会吞掉另一类的可用额——这正是团体保单超赔/少赔的经典成因。
 *
 * <p><b>三式校验</b>（§3.4）不是可选项：{@code ①} 与 {@code ②} 只在"三方支付 = 0 且不合理 = 0"时等价。 引擎默认取 ①（扣三方），若调用方送来 ②/③
 * 的口径（{@code validAmount} 未扣三方）， 会<b>把第三方已赔的钱再赔一次</b>。故此处提供 {@link #reconcile} 把差异暴露成诊断，而不是静默相信入参。
 */
public record FeePools(List<Pool> pools) {

  /** 承担费用类型（V1 §3.6；{@code CUSTOM} 承载自定义新增类型）。 */
  public enum Bucket {
    REASONABLE,
    SELF_PAY_2,
    TOTAL_SELF_PAY,
    CUSTOM
  }

  /**
   * 一个费用池的可赔基数。
   *
   * @param inScope 医保范围内金额（统筹后个人仍自担、但属于医保目录内的部分）
   * @param outOfScope 医保范围外金额（自付二 / 总自费 / 目录外）
   * @param label 展示名（{@code CUSTOM} 时为自定义类型名，其余为枚举名）
   */
  public record Pool(Bucket bucket, String label, BigDecimal inScope, BigDecimal outOfScope) {

    public Pool {
      inScope = Money.nz(inScope);
      outOfScope = Money.nz(outOfScope);
      label = label == null ? bucket.name() : label;
    }

    /** 池基数（范围内 + 范围外）。 */
    public BigDecimal total() {
      return inScope.add(outOfScope);
    }

    /** 该池在给定范围内口径下的基数（{@code socialOnly} 时只剩范围内）。 */
    public BigDecimal base(boolean inScopeOnly) {
      return inScopeOnly ? inScope : total();
    }
  }

  public FeePools {
    pools = List.copyOf(pools);
  }

  /**
   * 归集明细（V1 §3.5）。
   *
   * <p>归集口径逐条对齐条款：
   *
   * <ul>
   *   <li>合理金额：{@code valid_amount}（已由上游按 §3.4 ① 扣三方与核减）；{@code social_flag=IN} 计范围内， 其余计范围外；
   *   <li>自付二：{@code self_pay_2}，<b>全部计范围外</b>（乙类先行自付属目录外自担）；
   *   <li>总自费：{@code self_pay_c}（丙类 + 超限价），<b>全部计范围外</b>；
   *   <li>无明细的纯头行（{@code itemId == null}）跳过——LEFT JOIN 语义，否则赔案头金额会被重复计入。
   * </ul>
   *
   * @param rows 明细投影
   * @param socialOnly 责任是否"仅赔医保范围内"（R-DIR-01）
   */
  public static FeePools aggregate(List<ClaimWithItemsProjection> rows, boolean socialOnly) {
    BigDecimal reasonableIn = Money.ZERO;
    BigDecimal reasonableOut = Money.ZERO;
    BigDecimal selfPay2 = Money.ZERO;
    BigDecimal selfPayC = Money.ZERO;
    for (ClaimWithItemsProjection row : rows) {
      if (row == null || row.getItemId() == null) {
        continue;
      }
      BigDecimal valid = Money.nz(row.getItemValidAmount());
      boolean inSocialScope = "IN".equalsIgnoreCase(row.getItemSocialFlag());
      if (inSocialScope) {
        reasonableIn = reasonableIn.add(valid);
      } else {
        reasonableOut = reasonableOut.add(valid);
      }
      selfPay2 = selfPay2.add(Money.nz(row.getItemSelfPay2()));
      selfPayC = selfPayC.add(Money.nz(row.getItemSelfPayC()));
    }
    List<Pool> pools = new ArrayList<>(3);
    // socialOnly 时范围外合理金额不参与赔付，但**仍要登记**：归因明细必须能解释"钱为什么没赔"，
    // 直接丢掉会让 I1（Σ各费用类型 = 责任理算金额）与客户看到的发票金额对不上。
    pools.add(
        new Pool(Bucket.REASONABLE, "合理金额", reasonableIn, socialOnly ? Money.ZERO : reasonableOut));
    pools.add(new Pool(Bucket.SELF_PAY_2, "自付二", Money.ZERO, socialOnly ? Money.ZERO : selfPay2));
    pools.add(
        new Pool(Bucket.TOTAL_SELF_PAY, "总自费", Money.ZERO, socialOnly ? Money.ZERO : selfPayC));
    return new FeePools(pools);
  }

  /**
   * 归集明细，并把"因 socialOnly 被剔除"的范围外金额单独记入诊断（用于拒赔五要素的事实认定）。
   *
   * @return 池 + 被剔除金额
   */
  public static Aggregation aggregateDetailed(
      List<ClaimWithItemsProjection> rows, boolean socialOnly) {
    BigDecimal excludedOutOfScope = Money.ZERO;
    if (socialOnly) {
      for (ClaimWithItemsProjection row : rows) {
        if (row == null || row.getItemId() == null) {
          continue;
        }
        if (!"IN".equalsIgnoreCase(row.getItemSocialFlag())) {
          excludedOutOfScope = excludedOutOfScope.add(Money.nz(row.getItemValidAmount()));
        }
      }
    }
    return new Aggregation(aggregate(rows, socialOnly), excludedOutOfScope);
  }

  /** 归集结果（池 + 被 socialOnly 剔除的范围外合理金额）。 */
  public record Aggregation(FeePools pools, BigDecimal excludedOutOfScope) {}

  /** 取指定池（缺失返回零池，便于调用方不必判空）。 */
  public Pool pool(Bucket bucket) {
    for (Pool pool : pools) {
      if (pool.bucket() == bucket) {
        return pool;
      }
    }
    return new Pool(bucket, bucket.name(), Money.ZERO, Money.ZERO);
  }

  /**
   * 三式一致性核对（§3.4）：{@code ① = ② − 三方已赔 − 不合理}。
   *
   * <p>本引擎的 {@code REASONABLE} 池即 ① 口径；调用方若送来 ②/③（未扣三方），差额应接近 {@code Σ
   * third_party_paid}。此处把这些量算成<b>可断言的差异</b>，让"重复赔付"在数据上可见， 而不是靠人读代码判断。
   */
  public Reconciliation reconcile(List<ClaimWithItemsProjection> rows) {
    BigDecimal thirdPartyPaid = Money.ZERO;
    BigDecimal invalid = Money.ZERO;
    for (ClaimWithItemsProjection row : rows) {
      if (row == null || row.getItemId() == null) {
        continue;
      }
      thirdPartyPaid = thirdPartyPaid.add(Money.nz(row.getItemThirdPartyPaid()));
      invalid = invalid.add(Money.nz(row.getItemInvalidAmount()));
    }
    BigDecimal reasonable = pool(Bucket.REASONABLE).total();
    // ② 口径（不扣三方）：合理 + 三方 + 不合理 —— 若等于 ①，说明入参已按 ① 扣过，无重复赔付风险。
    BigDecimal grossReasonable = reasonable.add(thirdPartyPaid).add(invalid);
    return new Reconciliation(reasonable, thirdPartyPaid, invalid, grossReasonable);
  }

  /** 三式核对结果。 */
  public record Reconciliation(
      BigDecimal reasonable, BigDecimal thirdPartyPaid, BigDecimal invalid, BigDecimal gross) {}
}
