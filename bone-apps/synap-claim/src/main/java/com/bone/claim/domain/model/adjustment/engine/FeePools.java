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
 * <p><b>不可获利（损失补偿）原则</b>（§3.4 / D6）：统筹支付（社保）与第三方已赔付（商保/公账/其他基金）是"别人已替被保险人付过的钱"， 必须从可赔基数扣除，否则"赔付 +
 * 已付"会超过实际损失（超赔）。本类在 {@link #aggregate} 归集时即净扣， 再用 {@link #reconcile} 核对"已付不得超合理"这一硬约束——与
 * reference-impl S17 口径一致（第三方已付 = 社保统筹 + 其他保险已赔）。
 */
public record FeePools(List<Pool> pools, List<ItemContribution> contributions) {

  /**
   * 单费用项在费用池内的基数贡献（GAP⑨ 逐行分位归因用：把池级最终赔付下钻到费用项明细行）。
   *
   * <p>同一张发票的多个费用项会被归集进同一费用池，但理算结果须能<b>逐费用项</b>解释赔付来源， 故在归集时即保留「项 → 池」的基数映射，供引擎在池级计算完成后按权重分摊回费用项（Σ
   * 各费用项 ≡ 池）。
   */
  public record ItemContribution(
      Long itemId, String itemName, Bucket bucket, BigDecimal amount, boolean inScope) {
    public ItemContribution {
      amount = Money.nz(amount);
      itemName = itemName == null ? "" : itemName;
    }
  }

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
    contributions = List.copyOf(contributions);
  }

  /**
   * 归集明细（V1 §3.5）。
   *
   * <p>归集口径逐条对齐条款：
   *
   * <ul>
   *   <li>合理金额：{@code valid_amount} 为基数，<b>再在此处净扣统筹与第三方已付</b>；{@code social_flag=IN} 计范围内，其余计范围外；
   *   <li>自付二：{@code self_pay_2}，<b>全部计范围外</b>（乙类先行自付属目录外自担）；
   *   <li>总自费：{@code self_pay_c}（丙类 + 超限价），<b>全部计范围外</b>；
   *   <li>无明细的纯头行（{@code itemId == null}）跳过——LEFT JOIN 语义，否则赔案头金额会被重复计入。
   * </ul>
   *
   * <p><b>{@code valid_amount} 到底含哪些扣减（别搞错，错了就是双扣或少赔）</b>：它由初审核定的 {@code
   * ClaimItem#approveValidAmount} 写入，口径是「明细金额 − 核减（不合理费用）」， 差额自动落到 {@code
   * invalid_amount}。也就是说<b>它尚未扣统筹与第三方已付</b>——这两项正是本方法在下面 {@code priorPaid} 处扣除的。据此：
   *
   * <ul>
   *   <li>「核减」在上游做一次（进 {@code valid_amount}），引擎不再重复扣 {@code invalid_amount}；
   *   <li>「统筹 + 第三方已付」在<b>本方法</b>做一次，是全链唯一的扣除点；
   *   <li>两者都不得在另一侧再扣一遍：多扣 = 少赔（客户吃亏），漏扣 = 超赔（不可获利原则被破）。
   * </ul>
   *
   * @param rows 明细投影
   * @param socialOnly 责任是否"仅赔医保范围内"（R-DIR-01）
   */
  public static FeePools aggregate(List<ClaimWithItemsProjection> rows, boolean socialOnly) {
    List<ItemContribution> contributions = new ArrayList<>();
    BigDecimal reasonableIn = Money.ZERO;
    BigDecimal reasonableOut = Money.ZERO;
    BigDecimal selfPay2 = Money.ZERO;
    BigDecimal selfPayC = Money.ZERO;
    for (ClaimWithItemsProjection row : rows) {
      if (row == null || row.getItemId() == null) {
        continue;
      }
      BigDecimal valid = Money.nz(row.getItemValidAmount());
      // 不可获利（损失补偿）原则：统筹/第三方已付是"别人已付"，必须从可赔基数扣除，否则赔付+已付超损失（D6）。
      // 与 oracle S17 口径一致：第三方已付 = 社保统筹支付 + 其他保险已赔。
      BigDecimal priorPaid =
          Money.nz(row.getItemPoolingAmount()).add(Money.nz(row.getItemThirdPartyPaid()));
      BigDecimal netReasonable = valid.subtract(priorPaid);
      if (netReasonable.signum() < 0) {
        // 已付超过合理费用属数据异常；此处钳零，真正的不可能由 checkSettlementConsistency 兜底拒绝。
        netReasonable = BigDecimal.ZERO;
      }
      boolean inSocialScope = "IN".equalsIgnoreCase(row.getItemSocialFlag());
      if (inSocialScope) {
        reasonableIn = reasonableIn.add(netReasonable);
      } else {
        reasonableOut = reasonableOut.add(netReasonable);
      }
      // 逐行分位归因（GAP⑨）：每个费用项在 REASONABLE 池内的基数贡献。
      // socialOnly 时范围外合理金额不参与赔付 ⇒ 其贡献记为 0（与池的 outOfScope=0 一致）。
      contributions.add(
          new ItemContribution(
              row.getItemId(),
              row.getItemName(),
              Bucket.REASONABLE,
              inSocialScope ? netReasonable : (socialOnly ? Money.ZERO : netReasonable),
              inSocialScope));
      BigDecimal sp2 = Money.nz(row.getItemSelfPay2());
      selfPay2 = selfPay2.add(sp2);
      contributions.add(
          new ItemContribution(
              row.getItemId(),
              row.getItemName(),
              Bucket.SELF_PAY_2,
              socialOnly ? Money.ZERO : sp2,
              false));
      BigDecimal spc = Money.nz(row.getItemSelfPayC());
      selfPayC = selfPayC.add(spc);
      contributions.add(
          new ItemContribution(
              row.getItemId(),
              row.getItemName(),
              Bucket.TOTAL_SELF_PAY,
              socialOnly ? Money.ZERO : spc,
              false));
    }
    List<Pool> pools = new ArrayList<>(3);
    // socialOnly 时范围外合理金额不参与赔付，但**仍要登记**：归因明细必须能解释"钱为什么没赔"，
    // 直接丢掉会让 I1（Σ各费用类型 = 责任理算金额）与客户看到的发票金额对不上。
    pools.add(
        new Pool(Bucket.REASONABLE, "合理金额", reasonableIn, socialOnly ? Money.ZERO : reasonableOut));
    pools.add(new Pool(Bucket.SELF_PAY_2, "自付二", Money.ZERO, socialOnly ? Money.ZERO : selfPay2));
    pools.add(
        new Pool(Bucket.TOTAL_SELF_PAY, "总自费", Money.ZERO, socialOnly ? Money.ZERO : selfPayC));
    return new FeePools(pools, contributions);
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
   * 不可获利核对（§3.4 / D6）：汇总"统筹+第三方已付"与不合理金额。
   *
   * <p>{@code REASONABLE} 池已是净可赔基数（{@link #aggregate} 在归集时即扣掉 {@code priorPaid}）， 故此处只需暴露 {@code
   * priorPaid}，供 {@code checkSettlementConsistency} 断言"已付不得超合理"。
   */
  public Reconciliation reconcile(List<ClaimWithItemsProjection> rows) {
    BigDecimal priorPaid = Money.ZERO;
    BigDecimal invalid = Money.ZERO;
    for (ClaimWithItemsProjection row : rows) {
      if (row == null || row.getItemId() == null) {
        continue;
      }
      priorPaid =
          priorPaid
              .add(Money.nz(row.getItemPoolingAmount()))
              .add(Money.nz(row.getItemThirdPartyPaid()));
      invalid = invalid.add(Money.nz(row.getItemInvalidAmount()));
    }
    // REASONABLE 池已是净可赔基数（aggregate 已扣 priorPaid），此处只核对"已付不得超合理"。
    BigDecimal reasonable = pool(Bucket.REASONABLE).total();
    return new Reconciliation(reasonable, priorPaid, invalid);
  }

  /** 不可获利核对结果：净可赔基数 + 已付合计（统筹+第三方）+ 不合理。 */
  public record Reconciliation(BigDecimal reasonable, BigDecimal priorPaid, BigDecimal invalid) {}
}
