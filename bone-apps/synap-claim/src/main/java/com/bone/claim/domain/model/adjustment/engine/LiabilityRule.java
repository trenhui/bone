package com.bone.claim.domain.model.adjustment.engine;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 责任规则（V1 §2 十一维的可执行子集）。
 *
 * <p><b>V1 的边界是设计决策，不是实现欠账</b>：原文明确"多责任组合情形"四个小节全空、 "先赔后赔三种关系的金额推导公式全部缺失"。在这种语料上猜一个多责任算法，做出来的必然是一份
 * 看起来完整、实则无法与保司对账的实现。故本类把<b>能确定的</b>（费用补偿型单责任闭环）做成绝对准确， 把<b>不能确定的</b>（定额给付、津贴给付、共享保额、先赔后赔、外部控额） 一律
 * {@link #validate()} 期显式拒绝并给出受控原因码，由编排转人工——而不是配了不生效。
 *
 * <p><b>日限额（Q-05）已从"拒绝"改为"实现"</b>：V1 §7.4 只给了两档口径名、没给算法，落地口径是 「同一业务日的赔付累计 ≤ 日限额」，业务日由 {@link
 * LimitSet#dailyBasis()} 决定（发票日期 / 赔案受理日）， 当日已赔额由跨案账本（{@code
 * Accumulation#dailyUsed}）给出；窗口日判不出来或账本记的是更晚的一天时 fail-closed（见 {@code
 * AdjustmentEngine}），不回落成"按未赔过算"。
 *
 * <p><b>十一维落点</b>：
 *
 * <table>
 *   <caption>V1 §2 配置要素 → 本类字段</caption>
 *   <tr><th>要素<th>落点
 *   <tr><td>1 责任形式<td>{@link #form}（仅费用补偿型通过校验）
 *   <tr><td>2 保额形式<td>{@link LimitSet}（固定保额 / 固定+个人 / 个单额度）
 *   <tr><td>3 适用对象<td>前置校验由编排处理（本类只带等待期）
 *   <tr><td>4 适用情形<td>{@link #coveredBuckets} + {@link #socialOnly}
 *   <tr><td>5 医疗适用<td>{@link PayRatioTable} 的医保维度
 *   <tr><td>6 适用限定<td>{@link #limitedConditions}（不符即拒，前置校验）
 *   <tr><td>7 等待期<td>{@link #waitingPeriodDays}
 *   <tr><td>8 免赔设置<td>{@link DeductibleRule}
 *   <tr><td>9 赔付比例<td>{@link PayRatioTable}
 *   <tr><td>10 保额设置<td>{@link LimitSet}
 *   <tr><td>11 控额规则<td>{@link QuotaPolicy} + {@link LimitSet#occurrenceLimit()}
 * </table>
 */
public record LiabilityRule(
    String liabilityUuid,
    String liabilityVersion,
    LiabilityForm form,
    boolean socialOnly,
    Set<FeePools.Bucket> coveredBuckets,
    int waitingPeriodDays,
    DeductibleRule deductible,
    PayRatioTable ratios,
    LimitSet limits,
    QuotaPolicy quota,
    OccurrenceSpec occurrences,
    Relation relation,
    LimitedConditions limitedConditions,
    /** 发票级免赔（GAP②）：在比例之前、作用于各费用池基数；与年度免赔（{@link #deductible}）是两层独立免赔。 */
    DeductibleRule invoiceDeductible,
    /** 理算级免赔（GAP⑥）：在比例之后、控额之前，对理算金额再做一次绝对额扣减；是第二个免赔层。 */
    DeductibleRule adjustDeductible) {

  /** 责任形式（V1 §2 要素 1）。 */
  public enum LiabilityForm {
    /** 费用补偿型（V1 唯一支持）。 */
    REIMBURSEMENT,
    /** 定额给付型（V1 无规则）。 */
    FIXED_AMOUNT,
    /** 津贴给付型（V1 无规则）。 */
    ALLOWANCE
  }

  /** 保额形式（V1 §2 要素 2）。 */
  public enum InsuredForm {
    /** 固定保额：赔付限额。 */
    FIXED,
    /** 固定保额 + 个人额度：双重控额。 */
    FIXED_PLUS_PERSONAL,
    /** 基础保额。 */
    BASE,
    /** 个单额度：额度由客户保单提供，责任不设保额。 */
    POLICY_AMOUNT
  }

  /** 账户类型（V1 §1.3）：个账 / 公账；直付与保司属外部额度（V1 未定义，见 Q-07）。 */
  public enum AccountType {
    /** 个账：单被保险人在所涉责任上的可赔额度。 */
    PERSONAL,
    /** 公账：全保单共享（挂在直付保单上，V1 未定义额度来源）。 */
    PUBLIC,
    /** 直付控额：额度来自直付接口（外部系统实时余额）。 */
    DIRECT_PAY,
    /** 保司三方控额：额度来自保司接口。 */
    INSURER
  }

  /**
   * 日限额的「日」口径（V1 §7.4 的两档）。
   *
   * <p><b>为什么口径必须和金额一起配</b>：只有一个金额无法判定"同日"指哪一天——按发票日期与按赔案受理日 在补赔场景（9 月发票 10
   * 月赔）下会算出完全不同的日窗口。配了金额不配口径 ⇒ 装载期拒绝（{@link #validate()}），而不是替客户选一个。
   */
  public enum DailyBasis {
    /** 未配置日限额口径。 */
    NONE,
    /** 按同日发票累计（V1 §7.4 第一档）：窗口取发票日期。 */
    SAME_DAY_INVOICE,
    /** 按同日赔案累计（V1 §7.4 第二档）：窗口取赔案受理日。 */
    SAME_DAY_CLAIM;

    /** 解析配置值；空串为 {@link #NONE}，无法识别即 fail-closed（配错口径比不配更危险）。 */
    public static DailyBasis fromCode(String code) {
      if (code == null || code.isBlank()) {
        return NONE;
      }
      for (DailyBasis basis : values()) {
        if (basis.name().equalsIgnoreCase(code.trim())) {
          return basis;
        }
      }
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "无法识别的日限额口径「%s」：仅支持 NONE / SAME_DAY_INVOICE / SAME_DAY_CLAIM",
          code);
    }
  }

  /** 额度集合（V1 §2 要素 10 + §7）。 */
  public record LimitSet(
      InsuredForm insuredForm,
      BigDecimal personalLimit,
      BigDecimal liabilityLimit,
      BigDecimal productLimit,
      BigDecimal planLimit,
      BigDecimal sharedLimit,
      BigDecimal occurrenceLimit,
      BigDecimal dailyLimit,
      DailyBasis dailyBasis) {

    public LimitSet {
      personalLimit = Money.nz(personalLimit);
      liabilityLimit = Money.nz(liabilityLimit);
      productLimit = Money.nz(productLimit);
      planLimit = Money.nz(planLimit);
      sharedLimit = Money.nz(sharedLimit);
      occurrenceLimit = Money.nz(occurrenceLimit);
      dailyLimit = Money.nz(dailyLimit);
      dailyBasis = dailyBasis == null ? DailyBasis.NONE : dailyBasis;
    }

    /** 兼容构造（未配置日限额）：口径固定为 {@link DailyBasis#NONE}。 */
    public LimitSet(
        InsuredForm insuredForm,
        BigDecimal personalLimit,
        BigDecimal liabilityLimit,
        BigDecimal productLimit,
        BigDecimal planLimit,
        BigDecimal sharedLimit,
        BigDecimal occurrenceLimit,
        BigDecimal dailyLimit) {
      this(
          insuredForm,
          personalLimit,
          liabilityLimit,
          productLimit,
          planLimit,
          sharedLimit,
          occurrenceLimit,
          dailyLimit,
          DailyBasis.NONE);
    }

    /** 仅责任额度（旧配置 {@code liability_limit} 的等价映射；0 = 不封顶）。 */
    public static LimitSet liabilityOnly(BigDecimal liabilityLimit) {
      return new LimitSet(
          InsuredForm.FIXED,
          Money.ZERO,
          liabilityLimit,
          Money.ZERO,
          Money.ZERO,
          Money.ZERO,
          Money.ZERO,
          Money.ZERO,
          DailyBasis.NONE);
    }

    /** 责任额度是否不封顶（{@code 0} 或负 ⇒ 不封顶，对齐 reference-impl S25）。 */
    public boolean liabilityUncapped() {
      return liabilityLimit.signum() <= 0;
    }

    public boolean hasOccurrenceLimit() {
      return occurrenceLimit.signum() > 0;
    }

    public boolean hasDailyLimit() {
      return dailyLimit.signum() > 0;
    }

    /** 日限额是否真正生效（金额与口径都配了）。 */
    public boolean dailyActive() {
      return hasDailyLimit() && dailyBasis != DailyBasis.NONE;
    }

    /** 附日限额（金额 + 口径必须同时给，避免"配了金额没配日子"）。 */
    public LimitSet withDaily(BigDecimal limit, DailyBasis basis) {
      return new LimitSet(
          insuredForm,
          personalLimit,
          liabilityLimit,
          productLimit,
          planLimit,
          sharedLimit,
          occurrenceLimit,
          limit,
          basis);
    }
  }

  /** 控额策略（V1 §7.1 控额类型 + §7.2 扣减方式）。 */
  public record QuotaPolicy(AccountType accountType, QuotaStack.DeductionMode mode) {

    public QuotaPolicy {
      accountType = accountType == null ? AccountType.PERSONAL : accountType;
      mode = mode == null ? QuotaStack.DeductionMode.LIABILITY_ONLY : mode;
    }

    public static QuotaPolicy defaults() {
      return new QuotaPolicy(AccountType.PERSONAL, QuotaStack.DeductionMode.LIABILITY_ONLY);
    }
  }

  /** 次限额规格（V1 §7.3）。 */
  public record OccurrenceSpec(OccurrenceGrouper.Basis basis, BigDecimal limit) {

    public OccurrenceSpec {
      basis = basis == null ? OccurrenceGrouper.Basis.NONE : basis;
      limit = Money.nz(limit);
    }

    public static OccurrenceSpec none() {
      return new OccurrenceSpec(OccurrenceGrouper.Basis.NONE, Money.ZERO);
    }
  }

  /**
   * 责任间关系（V1 §8）：共享保额（共保代码）与先赔后赔。
   *
   * <p>两者在 V1 里都只有名称、没有金额公式。此处保留字段是为了<b>识别并拒绝</b>—— 配置里出现了就必须让人看到"这条责任不能自动理算"，而不是让引擎假装它是独立责任照算一遍。
   */
  public record Relation(String sharedCode, String priorLiabilityUuid) {

    public static Relation none() {
      return new Relation("", "");
    }

    public boolean hasAny() {
      return (sharedCode != null && !sharedCode.isBlank())
          || (priorLiabilityUuid != null && !priorLiabilityUuid.isBlank());
    }
  }

  /**
   * 适用限定（V1 §2 要素 6）：前置校验，不符即不赔。
   *
   * <p><b>只落 V1 六维里的两维——医院、病种</b>。理由是事实可得性与其它四维有本质差别：
   *
   * <ul>
   *   <li>医院 / 病种：{@code t_claim_case.hospital_code} / {@code diagnosis_code} 已在 V10 为「次」分组落地，
   *       案情自带编码，<b>不需要任何外部主数据</b>，可以现在就把比对做成确定行为；
   *   <li>区域 / 医保 / 药品 / 诊疗：判定要么依赖外部目录（药品/诊疗编码表），要么依赖保单层面的区域划分口径，
   *       本仓库无法闭环。这两维<b>当前没有配置列</b>——即"表达不出来"而不是"表达出来被静默忽略"； 接入时须同时补列，并在这里加 {@code
   *       LIMITED_CONDITION_DIMENSION_UNSUPPORTED} 之类的显式拒绝， 不允许出现"配了但引擎不读"的第三种状态。
   * </ul>
   *
   * <p><b>为什么不沿用旧的 {@code (enabled, hospitalCode, diseaseCode)} 三字段</b>：那三个字段既没进过配置映射 （生产恒为 {@code
   * disabled()}），形状上也只能表达"限一家医院 + 一个病种"、无法表达黑名单与名单集。 真实条款是"限以下医院之一 / 除以下病种"， 单值等值比对会把"限 A、B
   * 两家医院"这种最常见的写法逼成两条责任。
   *
   * @param mode 判定方式：{@code NONE} 不启用；{@code WHITELIST} 只赔名单内；{@code BLACKLIST} 名单内不赔
   * @param hospitalCodes 医院编码集合（按编码而非名称比对——名称可改，编码是身份）
   * @param diagnosisCodes 病种/诊断编码集合
   */
  public record LimitedConditions(
      Mode mode, Set<String> hospitalCodes, Set<String> diagnosisCodes) {

    /** 限定方式。混合模式（医院白名单 + 病种黑名单）V1 无依据，需由两条责任表达，故这里是单一模式。 */
    public enum Mode {
      /** 不启用（默认；与历史配置行为完全一致）。 */
      NONE,
      /** 白名单：只在名单内赔，名单外拒。 */
      WHITELIST,
      /** 黑名单：名单内拒，名单外赔。 */
      BLACKLIST;

      /** 解析配置值；空串为 {@link #NONE}，无法识别即 fail-closed（配错模式比不配更危险）。 */
      public static Mode fromCode(String code) {
        if (code == null || code.isBlank()) {
          return NONE;
        }
        for (Mode mode : values()) {
          if (mode.name().equalsIgnoreCase(code.trim())) {
            return mode;
          }
        }
        throw RuleGap.of(
            RuleGap.Code.INVALID_INPUT, "无法识别的适用限定方式「%s」：仅支持 NONE / WHITELIST / BLACKLIST", code);
      }
    }

    public LimitedConditions {
      mode = mode == null ? Mode.NONE : mode;
      hospitalCodes = ordered(hospitalCodes);
      diagnosisCodes = ordered(diagnosisCodes);
    }

    /** 去重 + 保留录入顺序（{@code Set.copyOf} 的顺序未定义，会让同一份配置每次报出不同的名单排列）。 */
    private static Set<String> ordered(Set<String> codes) {
      return codes == null || codes.isEmpty()
          ? Set.of()
          : java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(codes));
    }

    public static LimitedConditions disabled() {
      return new LimitedConditions(Mode.NONE, Set.of(), Set.of());
    }

    /**
     * 由责任配置装配。
     *
     * <p>{@code mode = NONE} 时<b>忽略名单</b>：否则"配了名单忘了开模式"会变成一条没有任何限定的责任， 且从库里看不出差别——而这正是"配了不生效"的典型来源。
     * 反过来"开了模式但名单为空"由 {@link LiabilityRule#validate()} 拒绝，两个方向都不留静默通道。
     *
     * @param mode 判定方式（由 {@link Mode#fromCode} 解析；无法识别即 fail-closed，不回落 {@code NONE}）
     * @param hospitalCsv 医院编码串（逗号 / 顿号 / 分号 / 空白分隔，均可）
     * @param diagnosisCsv 病种编码串（同一分隔规则）
     */
    public static LimitedConditions of(Mode mode, String hospitalCsv, String diagnosisCsv) {
      if (mode == null || mode == Mode.NONE) {
        return disabled();
      }
      return new LimitedConditions(mode, parseCodes(hospitalCsv), parseCodes(diagnosisCsv));
    }

    /**
     * 解析编码串。
     *
     * <p>只做 trim 与去空，<b>不折叠大小写</b>：编码是主数据身份，{@code H001} 与 {@code h001} 在对手方系统里可能是两条不同记录，
     * 折叠会让"改个大小写就绕过黑名单"。分隔符放宽是因为这些串由配置岗手工录入或从表格粘贴，全角逗号与顿号是常态。
     *
     * <p>返回<b>保留录入顺序</b>的集合：错误信息里要能按配置岗熟悉的顺序列出名单，{@code Set.copyOf} 的顺序未定义会让同一份配置
     * 每次报出不同的名单排列，排障时以为是数据变了。
     */
    public static Set<String> parseCodes(String csv) {
      if (csv == null || csv.isBlank()) {
        return Set.of();
      }
      Set<String> codes = new java.util.LinkedHashSet<>();
      for (String part : csv.split("[,，、;；\\s]+")) {
        String code = part.trim();
        if (!code.isEmpty()) {
          codes.add(code);
        }
      }
      return java.util.Collections.unmodifiableSet(codes);
    }

    /** 是否启用。 */
    public boolean active() {
      return mode != Mode.NONE;
    }

    /** 是否配了任一维度的名单（启用但两个名单都空属于配置不成立）。 */
    public boolean hasAnyCode() {
      return !hospitalCodes.isEmpty() || !diagnosisCodes.isEmpty();
    }
  }

  public LiabilityRule {
    coveredBuckets =
        coveredBuckets == null || coveredBuckets.isEmpty()
            ? EnumSet.allOf(FeePools.Bucket.class)
            : Set.copyOf(coveredBuckets);
    deductible = deductible == null ? DeductibleRule.none() : deductible;
    limits = limits == null ? LimitSet.liabilityOnly(Money.ZERO) : limits;
    quota = quota == null ? QuotaPolicy.defaults() : quota;
    occurrences = occurrences == null ? OccurrenceSpec.none() : occurrences;
    relation = relation == null ? Relation.none() : relation;
    limitedConditions =
        limitedConditions == null ? LimitedConditions.disabled() : limitedConditions;
    invoiceDeductible = invoiceDeductible == null ? DeductibleRule.none() : invoiceDeductible;
    adjustDeductible = adjustDeductible == null ? DeductibleRule.none() : adjustDeductible;
  }

  /**
   * 兼容构造（旧调用点仅传 13 个要素）：两个新增免赔层默认不启用（{@link DeductibleRule#none()}），
   * 行为与不新增免赔层时完全一致——保证既有测试与生产入口无需改动即可继续编译、运行。
   */
  public LiabilityRule(
      String liabilityUuid,
      String liabilityVersion,
      LiabilityForm form,
      boolean socialOnly,
      Set<FeePools.Bucket> coveredBuckets,
      int waitingPeriodDays,
      DeductibleRule deductible,
      PayRatioTable ratios,
      LimitSet limits,
      QuotaPolicy quota,
      OccurrenceSpec occurrences,
      Relation relation,
      LimitedConditions limitedConditions) {
    this(
        liabilityUuid,
        liabilityVersion,
        form,
        socialOnly,
        coveredBuckets,
        waitingPeriodDays,
        deductible,
        ratios,
        limits,
        quota,
        occurrences,
        relation,
        limitedConditions,
        DeductibleRule.none(),
        DeductibleRule.none());
  }

  /** 设置发票级免赔层（GAP②）：返回新规则实例，原实例不变。 */
  public LiabilityRule withInvoiceDeductible(DeductibleRule deductible) {
    return new LiabilityRule(
        liabilityUuid,
        liabilityVersion,
        form,
        socialOnly,
        coveredBuckets,
        waitingPeriodDays,
        this.deductible,
        ratios,
        limits,
        quota,
        occurrences,
        relation,
        limitedConditions,
        deductible,
        adjustDeductible);
  }

  /** 设置理算级免赔层（GAP⑥）：返回新规则实例，原实例不变。 */
  public LiabilityRule withAdjustDeductible(DeductibleRule deductible) {
    return new LiabilityRule(
        liabilityUuid,
        liabilityVersion,
        form,
        socialOnly,
        coveredBuckets,
        waitingPeriodDays,
        this.deductible,
        ratios,
        limits,
        quota,
        occurrences,
        relation,
        limitedConditions,
        invoiceDeductible,
        deductible);
  }

  /** 设置额度集合（日限额等需要整体替换 {@link LimitSet} 时使用）：返回新规则实例。 */
  public LiabilityRule withLimits(LimitSet limits) {
    return new LiabilityRule(
        liabilityUuid,
        liabilityVersion,
        form,
        socialOnly,
        coveredBuckets,
        waitingPeriodDays,
        this.deductible,
        ratios,
        limits,
        quota,
        occurrences,
        relation,
        limitedConditions,
        invoiceDeductible,
        adjustDeductible);
  }

  /** 设置适用限定（医院/病种白黑名单）：返回新规则实例，原实例不变。 */
  public LiabilityRule withLimitedConditions(LimitedConditions conditions) {
    return new LiabilityRule(
        liabilityUuid,
        liabilityVersion,
        form,
        socialOnly,
        coveredBuckets,
        waitingPeriodDays,
        this.deductible,
        ratios,
        limits,
        quota,
        occurrences,
        relation,
        conditions,
        invoiceDeductible,
        adjustDeductible);
  }

  /**
   * V1 默认规则：由最小标量集构造，免赔对象取<b>理算金额</b>（§6.3 默认，先乘比例再减免赔）。
   *
   * <p>发票 1000、比例 80%、免赔 200 时为 {@code 1000×80% − 200 = 600}。
   */
  public static LiabilityRule simple(
      String liabilityUuid,
      String liabilityVersion,
      BigDecimal payPercent,
      BigDecimal deductibleAmount,
      BigDecimal liabilityLimit,
      boolean socialOnly) {
    return build(
        liabilityUuid,
        liabilityVersion,
        payPercent,
        deductibleAmount,
        liabilityLimit,
        socialOnly,
        DeductibleRule.Target.CALCULATED_AMOUNT);
  }

  /**
   * 由<b>旧责任配置</b>（{@code t_adj_liability} 的 {@code pay_percent}/{@code liability_deduct}/ {@code
   * liability_limit}/{@code social_only}）映射。
   *
   * <p><b>为什么免赔对象是「发票费用」而不是 V1 默认的「理算金额」</b>：这四列是历史配置模型，它在原系统 （reference-impl
   * P16「免赔先于比例作用于费用基数」）里的语义就是"先减免赔再乘比例"。把旧数据按 V1 默认解释会 <b>改变所有存续保单的赔付金额</b>（发票 1000、比例 80%、免赔 200：旧
   * 640、V1 600）——这是资金口径变更，必须走条款报批， 不能由代码升级顺手完成。
   *
   * <p>因此本方法<b>忠实还原旧语义</b>；新配置请直接用 {@link #simple} 或显式构造 {@link DeductibleRule}。
   * 同一条责任只允许有一个映射入口，避免"新旧两套算法"。
   */
  public static LiabilityRule fromLegacy(
      String liabilityUuid,
      String liabilityVersion,
      BigDecimal payPercent,
      BigDecimal deductibleAmount,
      BigDecimal liabilityLimit,
      boolean socialOnly) {
    return fromLegacy(
        liabilityUuid,
        liabilityVersion,
        payPercent,
        deductibleAmount,
        liabilityLimit,
        socialOnly,
        Money.ZERO,
        Money.ZERO);
  }

  /**
   * 由<b>旧责任配置</b>映射，并显式带上发票级 / 理算级两个新增免赔层（GAP② / GAP⑥）。
   *
   * <p>两个新增免赔层默认不配置（{@code 0} ⇒ {@link DeductibleRule#none()}），与未启用时行为完全一致； 配置后由引擎在「比例前 /
   * 比例后」两个独立时点施加。
   */
  public static LiabilityRule fromLegacy(
      String liabilityUuid,
      String liabilityVersion,
      BigDecimal payPercent,
      BigDecimal deductibleAmount,
      BigDecimal liabilityLimit,
      boolean socialOnly,
      BigDecimal invoiceDeduct,
      BigDecimal adjustDeduct) {
    return fromLegacy(
        liabilityUuid,
        liabilityVersion,
        payPercent,
        deductibleAmount,
        liabilityLimit,
        socialOnly,
        invoiceDeduct,
        adjustDeduct,
        Money.ZERO,
        DailyBasis.NONE);
  }

  /**
   * 由<b>旧责任配置</b>映射，带齐三层免赔（年度 / 发票级 / 理算级）与日限额（Q-05）。
   *
   * <p>日限额在这里只做"金额 + 口径"的装配：{@code dailyLimit <= 0} 时口径归 {@link DailyBasis#NONE}， 与未配置完全一致；配了金额没配口径
   * （或反之）会被 {@link #validate()} 拒绝，不会静默退化成"不限额"。
   */
  public static LiabilityRule fromLegacy(
      String liabilityUuid,
      String liabilityVersion,
      BigDecimal payPercent,
      BigDecimal deductibleAmount,
      BigDecimal liabilityLimit,
      boolean socialOnly,
      BigDecimal invoiceDeduct,
      BigDecimal adjustDeduct,
      BigDecimal dailyLimit,
      DailyBasis dailyBasis) {
    return fromLegacy(
        liabilityUuid,
        liabilityVersion,
        payPercent,
        deductibleAmount,
        liabilityLimit,
        socialOnly,
        invoiceDeduct,
        adjustDeduct,
        dailyLimit,
        dailyBasis,
        LimitedConditions.disabled());
  }

  /**
   * 由<b>旧责任配置</b>映射，带齐三层免赔（年度 / 发票级 / 理算级）、日限额（Q-05）与适用限定（V1 §2 要素 6）。
   *
   * <p>适用限定在这里只做装配，<b>不做判定</b>：判定发生在 {@code checkPreconditions}，因为它需要案情事实（医院/病种编码），
   * 而事实不在责任配置里。装配与判定分离也保证了"装载期能发现的配置问题"与"必须看到案子才能发现的数据问题"不会混成一个错误。
   */
  public static LiabilityRule fromLegacy(
      String liabilityUuid,
      String liabilityVersion,
      BigDecimal payPercent,
      BigDecimal deductibleAmount,
      BigDecimal liabilityLimit,
      boolean socialOnly,
      BigDecimal invoiceDeduct,
      BigDecimal adjustDeduct,
      BigDecimal dailyLimit,
      DailyBasis dailyBasis,
      LimitedConditions limitedConditions) {
    LiabilityRule base =
        build(
            liabilityUuid,
            liabilityVersion,
            payPercent,
            deductibleAmount,
            liabilityLimit,
            socialOnly,
            DeductibleRule.Target.INVOICE_AMOUNT);
    LimitSet limits =
        dailyLimit == null || dailyLimit.signum() <= 0
            ? base.limits
            : base.limits.withDaily(dailyLimit, dailyBasis);
    return base.withLimits(limits)
        .withInvoiceDeductible(DeductibleRule.annualAbsolute(Money.nz(invoiceDeduct)))
        .withAdjustDeductible(DeductibleRule.annualAbsolute(Money.nz(adjustDeduct)))
        .withLimitedConditions(limitedConditions);
  }

  private static LiabilityRule build(
      String liabilityUuid,
      String liabilityVersion,
      BigDecimal payPercent,
      BigDecimal deductibleAmount,
      BigDecimal liabilityLimit,
      boolean socialOnly,
      DeductibleRule.Target target) {
    DeductibleRule deductible =
        deductibleAmount == null || deductibleAmount.signum() <= 0
            ? DeductibleRule.none()
            : new DeductibleRule(
                true,
                DeductibleRule.Kind.ABSOLUTE,
                DeductibleRule.Basis.ANNUAL,
                DeductibleRule.Form.AMOUNT,
                DeductibleRule.Offset.MEDICAL_AND_THIRD_PARTY,
                target,
                DeductibleRule.Granularity.LIABILITY,
                deductibleAmount,
                Money.ZERO,
                0,
                Map.of());
    return new LiabilityRule(
        liabilityUuid,
        liabilityVersion,
        LiabilityForm.REIMBURSEMENT,
        socialOnly,
        EnumSet.allOf(FeePools.Bucket.class),
        0,
        deductible,
        PayRatioTable.uniform(payPercent),
        LimitSet.liabilityOnly(liabilityLimit),
        QuotaPolicy.defaults(),
        OccurrenceSpec.none(),
        Relation.none(),
        LimitedConditions.disabled());
  }

  /**
   * 装载期自检：把"V1 无法自动理算"的配置拦在算钱之前。
   *
   * <p>每一条拒绝都对应文档里一个明确的〔缺〕，注释里带 Q 编号，便于业务补齐后逐条放开（放开时请同时把 该条从"拒绝"改为"实现"，并补对应不变量测试）。
   */
  public void validate() {
    if (form != LiabilityForm.REIMBURSEMENT) {
      throw RuleGap.of(
          RuleGap.Code.LIABILITY_FORM_UNSUPPORTED, "责任形式 %s 在 V1 无规则（Q-11：定额给付型/津贴给付型整章空白）", form);
    }
    if (limits.hasDailyLimit() && limits.dailyBasis() == DailyBasis.NONE) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "配置了日限额 %s 但未配「日」的口径（SAME_DAY_INVOICE 按同日发票 / SAME_DAY_CLAIM 按同日赔案）：" + "窗口日判不出来，日限额无法执行",
          limits.dailyLimit().toPlainString());
    }
    if (!limits.hasDailyLimit() && limits.dailyBasis() != DailyBasis.NONE) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "配置了日限额口径 %s 但日限额为 0：该口径永远不会生效（配了不生效 ⇒ 超赔）",
          limits.dailyBasis());
    }
    if (limitedConditions.active() && !limitedConditions.hasAnyCode()) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "启用了适用限定（%s）但医院与病种名单都为空：白名单空等于全不赔、黑名单空等于不设限，" + "两种都不是业务意图，须补齐名单或关闭限定",
          limitedConditions.mode());
    }
    if (relation.sharedCode != null && !relation.sharedCode.isBlank()) {
      throw RuleGap.of(
          RuleGap.Code.SHARED_LIMIT_UNSUPPORTED,
          "责任配置了共享保额（共保代码 %s）：池额度来源、耗尽回落、多池截断顺序均未定义（Q-02）",
          relation.sharedCode);
    }
    if (relation.priorLiabilityUuid != null && !relation.priorLiabilityUuid.isBlank()) {
      throw RuleGap.of(
          RuleGap.Code.PRIORITY_CHAIN_UNSUPPORTED,
          "责任配置了先赔后赔（前序责任 %s）：三种关系的金额推导公式缺失（Q-01）",
          relation.priorLiabilityUuid);
    }
    if (quota.accountType() == AccountType.DIRECT_PAY
        || quota.accountType() == AccountType.INSURER) {
      throw RuleGap.of(
          RuleGap.Code.EXTERNAL_QUOTA_UNSUPPORTED,
          "控额类型 %s 依赖外部实时余额：超时/幂等/对账口径未定义（Q-07）",
          quota.accountType());
    }
    if (quota.accountType() == AccountType.PUBLIC) {
      throw RuleGap.of(
          RuleGap.Code.LIMIT_LAYER_UNDEFINED, "公账控额：一保单仅一个公账且挂直付保单，V1 未定义本地账本读取口径（§1.3〔缺〕）");
    }
    OccurrenceGrouper.requireConsistent(occurrences.basis(), occurrences.limit());
    deductible.validate();
    invoiceDeductible.validate();
    adjustDeductible.validate();
    ratios.validate();
  }

  /** 承担该费用类型。 */
  public boolean covers(FeePools.Bucket bucket) {
    return coveredBuckets.contains(bucket);
  }

  /** 责任额度是否封顶。 */
  public boolean liabilityCapped() {
    return !limits.liabilityUncapped();
  }
}
