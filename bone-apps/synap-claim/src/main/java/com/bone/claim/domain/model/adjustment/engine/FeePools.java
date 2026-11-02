package com.bone.claim.domain.model.adjustment.engine;

import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
      Long itemId,
      String itemName,
      Bucket bucket,
      BigDecimal amount,
      boolean inScope,
      /** 费用类别编码（{@code t_claim_item.fee_code}）；空串 = 未分类。类别规则据此命中，逐项归因也据此分单元。 */
      String feeCode) {
    public ItemContribution {
      amount = Money.nz(amount);
      itemName = itemName == null ? "" : itemName;
      feeCode = feeCode == null ? "" : feeCode.trim();
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
    return aggregate(rows, socialOnly, java.util.Set.of());
  }

  /**
   * 归集明细，并按目录准入结论剔除（缺口 ① 费用明细筛选）。
   *
   * <p><b>为什么在归集处剔除、而不是在调用方过滤明细列表</b>：{@code rows} 的下标同时是「次」分组事实 （{@code
   * OccurrenceGrouper.Fact}）与逐行归因的下标基准。若在上游 {@code removeIf}，两个下标系立刻错位，
   * 分组会悄悄串组、归因会贴到别的费用项上——而且两者都不报错。故"剔哪几条"以集合形式传进来，{@code rows} 本体与下标保持不变。
   *
   * <p>被剔除的明细<b>不产生任何分池金额，也不产生逐行贡献</b>：目录外费用不属于可赔基数， 与"核减"（{@code invalid_amount}）同类。其金额由引擎单独计入
   * {@code catalogExcluded} 诊断与快照， 保证"为什么少赔"可回答。
   *
   * @param excludedItemIds 目录判定为目录外的 {@code itemId}；空集 = 不做目录剔除
   */
  public static FeePools aggregate(
      List<ClaimWithItemsProjection> rows,
      boolean socialOnly,
      java.util.Set<Long> excludedItemIds) {
    List<ItemContribution> contributions = new ArrayList<>();
    BigDecimal reasonableIn = Money.ZERO;
    BigDecimal reasonableOut = Money.ZERO;
    BigDecimal selfPay2 = Money.ZERO;
    BigDecimal selfPayC = Money.ZERO;
    for (ClaimWithItemsProjection row : rows) {
      if (row == null || row.getItemId() == null) {
        continue;
      }
      if (excludedItemIds != null && excludedItemIds.contains(row.getItemId())) {
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
              inSocialScope,
              feeCodeOf(row)));
      BigDecimal sp2 = Money.nz(row.getItemSelfPay2());
      selfPay2 = selfPay2.add(sp2);
      contributions.add(
          new ItemContribution(
              row.getItemId(),
              row.getItemName(),
              Bucket.SELF_PAY_2,
              socialOnly ? Money.ZERO : sp2,
              false,
              feeCodeOf(row)));
      BigDecimal spc = Money.nz(row.getItemSelfPayC());
      selfPayC = selfPayC.add(spc);
      contributions.add(
          new ItemContribution(
              row.getItemId(),
              row.getItemName(),
              Bucket.TOTAL_SELF_PAY,
              socialOnly ? Money.ZERO : spc,
              false,
              feeCodeOf(row)));
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
    return aggregateDetailed(rows, socialOnly, java.util.Set.of());
  }

  /**
   * 归集明细（含目录剔除），并把"因 socialOnly 被剔除"的范围外金额单独记入诊断。
   *
   * <p>注意两个"剔除"是两件事，不可合并：{@code socialOnly} 剔的是<b>医保范围外</b>（金额仍留在池里作为
   * outOfScope，只是不参与赔付，用于解释"为什么没赔"）；目录剔除是<b>不进池</b>（不属于可赔基数）。
   *
   * @param excludedItemIds 目录判定为目录外的 {@code itemId}
   */
  public static Aggregation aggregateDetailed(
      List<ClaimWithItemsProjection> rows,
      boolean socialOnly,
      java.util.Set<Long> excludedItemIds) {
    BigDecimal excludedOutOfScope = Money.ZERO;
    if (socialOnly) {
      for (ClaimWithItemsProjection row : rows) {
        if (row == null || row.getItemId() == null) {
          continue;
        }
        if (excludedItemIds != null && excludedItemIds.contains(row.getItemId())) {
          continue;
        }
        if (!"IN".equalsIgnoreCase(row.getItemSocialFlag())) {
          excludedOutOfScope = excludedOutOfScope.add(Money.nz(row.getItemValidAmount()));
        }
      }
    }
    return new Aggregation(aggregate(rows, socialOnly, excludedItemIds), excludedOutOfScope);
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
   * 按「费用类型 × 费用类别」切成可赔基数单元（费用类别规则的执行单元）。
   *
   * <p><b>为什么要切到这一层</b>：类别规则（手术费 100%、材料费 60% 且限 5000）的判定单位是"某类别的费用"，
   * 而费用池的单位是"某医保段（范围内/自付二/总自费）"。同一个类别会横跨多个费用池（合理金额 + 自付二 + 总自费），
   * 同一个费用池也会横跨多个类别——两个维度正交，必须做笛卡尔切分，否则"按类别打折"和"按费用段控额" 会互相覆盖：先按池算完再按类别拆，等于把池级金额按名头重贴标签。
   *
   * <p><b>为什么从 {@link #contributions()} 汇总而不是再扫一遍明细</b>：贡献记录是逐项归因的唯一事实源， 且已包含"统筹+第三方已付净扣"与 {@code
   * socialOnly} 的置零处理。另写一条汇总路径必然在某次改动后与归集口径分叉， 而分叉只在"某类别既有范围内又有范围外费用"的案子上暴露。
   *
   * <p><b>为什么丢掉金额全为零的切片</b>：那意味着该类别的这类费用压根没发生（或已被上游核减）， 给它出行只会让明细里堆满 0 元噪声；而"有金额但基数被 socialOnly
   * 剔除"的切片必须保留——那是要向客户解释的少赔原因。
   */
  public List<Slice> slices() {
    Map<String, Slice> merged = new java.util.LinkedHashMap<>();
    for (ItemContribution item : contributions) {
      String key = item.bucket().name() + "/" + item.feeCode();
      Slice current =
          merged.computeIfAbsent(
              key, ignored -> new Slice(item.bucket(), item.feeCode(), Money.ZERO, Money.ZERO));
      merged.put(key, current.plus(item.amount(), item.inScope()));
    }
    List<Slice> slices = new ArrayList<>(merged.size());
    for (Slice slice : merged.values()) {
      if (slice.total().signum() > 0) {
        slices.add(slice);
      }
    }
    return List.copyOf(slices);
  }

  /**
   * 一个「费用类型 × 费用类别」切片的基数。
   *
   * @param inScope 医保范围内金额
   * @param outOfScope 医保范围外金额（自付二 / 总自费 / 目录外）
   */
  public record Slice(Bucket bucket, String feeCode, BigDecimal inScope, BigDecimal outOfScope) {

    public Slice {
      inScope = Money.nz(inScope);
      outOfScope = Money.nz(outOfScope);
      feeCode = feeCode == null ? "" : feeCode.trim();
    }

    Slice plus(BigDecimal amount, boolean inScopeAmount) {
      BigDecimal money = Money.nz(amount);
      return inScopeAmount
          ? new Slice(bucket, feeCode, inScope.add(money), outOfScope)
          : new Slice(bucket, feeCode, inScope, outOfScope.add(money));
    }

    public BigDecimal total() {
      return inScope.add(outOfScope);
    }

    /** 该切片在给定范围内口径下的基数（{@code socialOnly} 时只剩范围内）。 */
    public BigDecimal base(boolean inScopeOnly) {
      return inScopeOnly ? inScope : total();
    }

    /** 是否未分类（{@code fee_code} 缺失）：类别规则不会命中，引擎会落诊断提示补数据。 */
    public boolean unclassified() {
      return feeCode.isEmpty();
    }
  }

  /** 明细的费用类别编码（缺列/空值统一成空串，避免 {@code null} 与 {@code ""} 在分组键上分成两类）。 */
  private static String feeCodeOf(ClaimWithItemsProjection row) {
    return row.getItemFeeCode() == null ? "" : row.getItemFeeCode().trim();
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
