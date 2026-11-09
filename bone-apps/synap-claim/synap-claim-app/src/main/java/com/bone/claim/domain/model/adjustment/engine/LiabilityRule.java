package com.bone.claim.domain.model.adjustment.engine;

import com.bone.claim.domain.model.liability.LiabilityReadMode;
import com.bone.claim.domain.model.liability.LiabilityRuleJson;
import com.bone.claim.domain.model.liability.LiabilityRuleJsonView;
import com.bone.claim.domain.model.liability.RuleValueDiff;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    DeductibleRule adjustDeductible,
    /**
     * 费用类别规则（{@code t_adj_liability_fee_rule}）：在统一比例/限额之上按<b>费用类别</b>差异化。
     *
     * <p><b>为什么必须进规则而不是留在配置面</b>：规则存了不被引擎读取时，界面写着"手术费 60%"、实赔按责任统一比例，
     * 且快照里看不出这条规则被忽略过——事后无法复盘，只能等客户对账时才发现。<b>能配的必须能算</b>， 这是本类把类别规则收进规则集的唯一理由。
     *
     * <p>空集合表示"未做类别细化"，引擎行为与引入前<b>逐字一致</b>（不拆单元、不改快照、不改比例命中记录）。
     */
    List<FeeCategoryRule> feeRules) {

  /** 责任形式（V1 §2 要素 1）。 */
  public enum LiabilityForm {
    /** 费用补偿型（V1 唯一支持）。 */
    REIMBURSEMENT,
    /** 定额给付型（V1 无规则）。 */
    FIXED_AMOUNT,
    /** 津贴给付型（V1 无规则）。 */
    ALLOWANCE;

    /**
     * 解析配置列 {@code liability_type} 的原始值。
     *
     * <p><b>null / 空串按 {@link #REIMBURSEMENT} 处理</b>：{@code AdjLiability} 建库时就把空值归一成该值，存量行没有这个语义
     * 之外的可能，故这是"还原默认值"而不是"猜"。
     *
     * <p><b>非法值直接拒绝，不回落</b>：回落正是本次修复要消灭的行为——一个拼错的形式被当成报销型，会静默按比例赔付。 这里抛 {@link RuleGap} 而非 {@link
     * IllegalArgumentException}，是为了让它与其它装载期拒绝走同一条受控出口（转人工 / 配置面报错）， 而不是变成 500。
     */
    public static LiabilityForm of(String raw) {
      if (raw == null || raw.isBlank()) {
        return REIMBURSEMENT;
      }
      String normalized = raw.trim().toUpperCase(Locale.ROOT);
      for (LiabilityForm form : values()) {
        if (form.name().equals(normalized)) {
          return form;
        }
      }
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "责任形式「%s」无法识别（合法值 REIMBURSEMENT / FIXED_AMOUNT / ALLOWANCE）：" + "无法确定用哪种算法算钱，须先修配置",
          raw);
    }
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

  /**
   * 费用类别规则（责任 × 费用类别）：一条 = 该类别在本责任下的赔付比例、次内限额、社保内外口径。
   *
   * <p><b>五个口径都是拍板结论，不是实现细节</b>（条款语料里没有"类别规则与责任统一比例谁优先"的原文， 但三种选择会产生不同金额，必须显式定一个并在此固定下来）：
   *
   * <ol>
   *   <li><b>类别规则优先于责任统一比例</b>：配了该类别就按类别比例算，未配的类别<b>继承</b>责任统一比例。
   *       不采用"未配即拒赔"——那会把所有未做类别细化的历史配置一并变成不可赔，属于用配置缺省制造拒赔；
   *       也不采用"两者取严"——条款里类别比例是<b>约定值</b>而非上限，取严会把"手术费 100%"永久压到责任比例之下；
   *   <li><b>类别限额是该类别在本「次」内的赔付上限（子限额）</b>：截断发生在免赔与比例之后、控额栈之前，
   *       被截掉的部分不占责任额度；它<b>不</b>单开额度池——赔款仍从责任额度里扣，责任额度是总闸；
   *   <li><b>免赔仍然只扣一次</b>（责任级）：按类别各自扣一份会放大免赔额，属系统性少赔，且金额"看起来更保守"、 复核时几乎不会发现；
   *   <li><b>社保内外取严</b>：类别 {@code socialOnly=1} 与责任 {@code socialOnly} 是「与」关系——任一要求社保内，
   *       该类别就只赔范围内金额。取严不会超赔，取宽则直接违反"仅赔社保内"的约定；
   *   <li><b>未分类明细不猜类别</b>：明细缺 {@code fee_code} 时按责任统一比例算并落诊断，由人工补数据； 按 4
   *       值粗类目（DRUG/SERVICE/EXAM/MATERIAL）推断 10 类费用类别属于猜——SERVICE 一项就覆盖床位/手术/ 治疗/护理/诊查/化验，猜错即错赔。
   * </ol>
   *
   * @param feeCode 费用类别编码（{@code t_ref_fee_category.fee_code}，服务层已校验存在性）
   * @param feeName 类别名称（冗余，供快照与诊断展示）
   * @param payPercent 该类别赔付比例（百分数 0–100，<b>总是覆盖</b>责任统一比例）
   * @param limitAmount 该类别次内赔付上限（{@code 0} = 不限）
   * @param socialOnly 该类别是否仅赔社保范围内
   */
  public record FeeCategoryRule(
      String feeCode,
      String feeName,
      BigDecimal payPercent,
      BigDecimal limitAmount,
      boolean socialOnly) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public FeeCategoryRule {
      feeCode = feeCode == null ? "" : feeCode.trim();
      feeName = feeName == null ? "" : feeName.trim();
      payPercent = Money.nz(payPercent);
      limitAmount = Money.nz(limitAmount);
      if (feeCode.isEmpty()) {
        throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "费用类别规则缺少类别编码：无法与明细归类结果对上");
      }
      if (payPercent.signum() < 0 || payPercent.compareTo(HUNDRED) > 0) {
        // 比例越界在引擎里会被 percentToRate 钳到 100%，那时"配错"与"配成 100%"在账面上无法区分。
        // 配置面已经拦过一道，这里再拦一次：规则也可能来自快照复算或直连数据库的修补。
        throw RuleGap.of(
            RuleGap.Code.INVALID_INPUT,
            "费用类别「%s」的赔付比例 %s 超出 0–100",
            feeCode,
            payPercent.toPlainString());
      }
      if (limitAmount.signum() < 0) {
        throw RuleGap.of(
            RuleGap.Code.INVALID_INPUT, "费用类别「%s」的限额 %s 为负", feeCode, limitAmount.toPlainString());
      }
    }

    /** 是否配置了类别限额（{@code 0} = 不限，与"限额 0 = 一分不赔"区分开）。 */
    public boolean hasLimit() {
      return limitAmount.signum() > 0;
    }

    /** 展示名：优先类别名称，缺名称回落编码（诊断与快照里不能出现空标签）。 */
    public String label() {
      return feeName.isEmpty() ? feeCode : feeName;
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

    /**
     * 责任额度是否不封顶。
     *
     * <p><b>契约语义</b>：{@code 0} 表示不封顶（不填即落库 0），对齐 reference-impl S25 「额度 <b>0</b> 表示不限制」（见 {@code
     * AdjustmentDualTrackFixture} 场景 S25-零额度不封顶）。
     *
     * <p><b>为什么判据是 {@code <= 0} 而不是 {@code == 0}</b>：配置写入期已拒绝负数 （{@code
     * LiabilityConfigApplicationService#assertRuleUsable}），负数只可能来自 ①写入期收紧之前的历史存量
     * ②绕过应用层的直连写入。理算期把它按「不封顶」兜底， 而不是当成「负限额」——后者会让所有赔案算出 0 赔付（比不限额更糟）。
     * 即<b>读取期容错</b>，而非配置面认可的语义：新数据不会出现负数。
     */
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

    public static Relation of(String sharedCode, String priorLiabilityUuid) {
      return new Relation(
          sharedCode == null ? "" : sharedCode,
          priorLiabilityUuid == null ? "" : priorLiabilityUuid);
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
    feeRules = normalizeFeeRules(feeRules);
  }

  /**
   * 归一化费用类别规则：去重校验 + 按编码定序。
   *
   * <p><b>为什么要定序</b>：快照里的规则数组参与 {@code formula_hash}，若顺序随数据库返回行序漂移，
   * 同一份配置两次理算会得出两个哈希，"同输入同输出"当场失效（复算校验会把正常案子判成被篡改）。 类别编码是规则的身份，按它定序即可稳定。同一类别出现两条同样不可接受：哪条生效取决于行序，
   * 等于把金额交给数据库返回顺序决定。
   */
  private static List<FeeCategoryRule> normalizeFeeRules(List<FeeCategoryRule> rules) {
    if (rules == null || rules.isEmpty()) {
      return List.of();
    }
    Map<String, FeeCategoryRule> byCode = new LinkedHashMap<>();
    for (FeeCategoryRule rule : rules) {
      if (rule == null) {
        continue;
      }
      if (byCode.putIfAbsent(rule.feeCode(), rule) != null) {
        throw RuleGap.of(
            RuleGap.Code.INVALID_INPUT,
            "费用类别「%s」配置了多条规则：哪条生效取决于行序，等于让数据库返回顺序决定赔付金额",
            rule.feeCode());
      }
    }
    List<FeeCategoryRule> ordered = new ArrayList<>(byCode.values());
    ordered.sort(Comparator.comparing(FeeCategoryRule::feeCode));
    return List.copyOf(ordered);
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
        DeductibleRule.none(),
        List.of());
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
        adjustDeductible,
        feeRules);
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
        deductible,
        feeRules);
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
        adjustDeductible,
        feeRules);
  }

  /** 设置共享额度关系（共保代码 / 先赔后赔前序责任）：返回新规则实例，原实例不变。 */
  public LiabilityRule withRelation(Relation relation) {
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
        adjustDeductible,
        feeRules);
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
        adjustDeductible,
        feeRules);
  }

  /** 设置费用类别规则（{@code t_adj_liability_fee_rule} 的生效快照）：返回新规则实例，原实例不变。 */
  public LiabilityRule withFeeRules(List<FeeCategoryRule> rules) {
    return new LiabilityRule(
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
        invoiceDeductible,
        adjustDeductible,
        rules);
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
        DeductibleRule.Target.CALCULATED_AMOUNT,
        LiabilityForm.REIMBURSEMENT);
  }

  /**
   * 由<b>旧责任配置</b>（{@code t_adj_liability}）映射，带齐三层免赔（年度 / 发票级 / 理算级）、日限额（Q-05）与适用限定（V1 §2 要素 6）。
   *
   * <p><b>为什么免赔对象是「发票费用」而不是 V1 默认的「理算金额」</b>：这些列是历史配置模型，它在原系统 （reference-impl
   * P16「免赔先于比例作用于费用基数」）里的语义就是"先减免赔再乘比例"。把旧数据按 V1 默认解释会 <b>改变所有存续保单的赔付金额</b>（发票 1000、比例 80%、免赔 200：旧
   * 640、V1 600）——这是资金口径变更，必须走条款报批， 不能由代码升级顺手完成。因此本方法<b>忠实还原旧语义</b>。
   *
   * <p><b>责任形式必须由调用方显式传入</b>（2026-11-08 修正）：此前 {@code form} 在这里被硬编码成 {@link
   * LiabilityForm#REIMBURSEMENT}，于是 {@link #validate()} 里 {@code LIABILITY_FORM_UNSUPPORTED}
   * 分支在生产路径 <b>永不可达</b>——责任配成定额/津贴给付型时，引擎会拿 {@code pay_percent} 当报销比例算钱，既不报错也无从归因（"配了不生效"的
   * 最坏形态：配错还算钱）。现在 {@code liabilityType} 来自配置列 {@code liability_type}，非报销型在这里装配成真实形式， 由 {@link
   * #validate()} 显式拒绝并转人工；配置面仍允许建档（见 {@link #validate()} 注释）。
   *
   * <p><b>单一装配入口</b>：曾有三个只带部分参数的便捷重载，它们必然把形式回落成报销型——每多一个入口就多一条"忘记传责任形式"的 路径，故一并删除（全仓无调用）。新代码请用
   * {@link #simple}（V1 报销型默认）或本方法。
   *
   * <p>适用限定在这里只做装配，<b>不做判定</b>：判定发生在 {@code checkPreconditions}，因为它需要案情事实（医院/病种编码），
   * 而事实不在责任配置里。装配与判定分离也保证了"装载期能发现的配置问题"与"必须看到案子才能发现的数据问题"不会混成一个错误。
   */
  public static LiabilityRule fromLegacy(
      String liabilityUuid,
      String liabilityVersion,
      String liabilityType,
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
            DeductibleRule.Target.INVOICE_AMOUNT,
            LiabilityForm.of(liabilityType));
    LimitSet limits =
        dailyLimit == null || dailyLimit.signum() <= 0
            ? base.limits
            : base.limits.withDaily(dailyLimit, dailyBasis);
    return base.withLimits(limits)
        .withInvoiceDeductible(DeductibleRule.annualAbsolute(Money.nz(invoiceDeduct)))
        .withAdjustDeductible(DeductibleRule.annualAbsolute(Money.nz(adjustDeduct)))
        .withLimitedConditions(limitedConditions);
  }

  /**
   * ③ 3a 双读入口：在旧标量路径之外，再按<b>单个键</b>回落算一遍并逐键比对。
   *
   * <p><b>为什么是重载而不是改原签名</b>：原 12 参数签名被两个生产调用点与全部既有夹具使用。改签名会把 「开关关闭」与「没有
   * JSON」两种完全不同的语义压进同一个参数，且任何一处漏改都会在编译期之外静默走错路。 重载让旧签名保持逐字不变，新语义只在显式传参时生效。
   *
   * <p><b>零行为变更的强制做法</b>：本方法<b>先</b>用原签名装配出旧标量路径的规则，<b>再</b>算比对；
   * 无论差异是否存在，返回的都是前者。这不是"顺手"，而是本阶段的全部承诺—— 3b 切主必须由后续 change 依据这里产出的差异率决定，而不是由这次改动偷偷完成。
   *
   * <p><b>差异判定用数值相等</b>（{@code compareTo}）：MySQL 8 的 JSON 列会把 {@code 90.00} 规范化成 {@code 90.0}
   * 并重排键序（② 阶段临时表实测），逐字比对必然误报，而误报会让作者关掉整道门禁。
   *
   * @param ruleView 读侧按键回落视图；{@code null} 表示该行聚合 JSON 为 NULL（历史行）
   * @param mode 读侧模式；{@link LiabilityReadMode#LEGACY_ONLY} 时不比对、不解析
   * @param diffs 差异收集器；{@code null} 表示调用方不关心差异（仅测试或纯读路径使用）
   */
  public static LiabilityRule fromLegacy(
      String liabilityUuid,
      String liabilityVersion,
      String liabilityType,
      BigDecimal payPercent,
      BigDecimal deductibleAmount,
      BigDecimal liabilityLimit,
      boolean socialOnly,
      BigDecimal invoiceDeduct,
      BigDecimal adjustDeduct,
      BigDecimal dailyLimit,
      DailyBasis dailyBasis,
      LimitedConditions limitedConditions,
      LiabilityRuleJsonView ruleView,
      LiabilityReadMode mode,
      java.util.List<RuleValueDiff> diffs) {
    LiabilityRule legacy =
        fromLegacy(
            liabilityUuid,
            liabilityVersion,
            liabilityType,
            payPercent,
            deductibleAmount,
            liabilityLimit,
            socialOnly,
            invoiceDeduct,
            adjustDeduct,
            dailyLimit,
            dailyBasis,
            limitedConditions);
    if (ruleView == null || mode != LiabilityReadMode.DUAL_READ) {
      return legacy;
    }
    collectDiff(
        diffs,
        liabilityUuid,
        liabilityVersion,
        LiabilityRuleJson.KEY_PAY_PERCENT,
        payPercent,
        ruleView);
    collectDiff(
        diffs,
        liabilityUuid,
        liabilityVersion,
        LiabilityRuleJson.KEY_LIABILITY_DEDUCT,
        deductibleAmount,
        ruleView);
    collectDiff(
        diffs,
        liabilityUuid,
        liabilityVersion,
        LiabilityRuleJson.KEY_LIABILITY_LIMIT,
        liabilityLimit,
        ruleView);
    return legacy;
  }

  /**
   * 单键比对：{@code resolve} 已保证"JSON 有值取 JSON、否则回落标量、两侧皆空即拒"， 故这里只需判"回落后的值是否与旧标量相等"。两侧皆空会在 {@code
   * resolve} 处抛出，不会走到比对。
   */
  private static void collectDiff(
      java.util.List<RuleValueDiff> diffs,
      String liabilityUuid,
      String liabilityVersion,
      String key,
      BigDecimal scalar,
      LiabilityRuleJsonView ruleView) {
    BigDecimal resolved = ruleView.resolve(key, scalar);
    if (scalar == null || resolved.compareTo(scalar) != 0) {
      if (diffs != null) {
        diffs.add(new RuleValueDiff(liabilityUuid, liabilityVersion, key, scalar, resolved));
      }
    }
  }

  private static LiabilityRule build(
      String liabilityUuid,
      String liabilityVersion,
      BigDecimal payPercent,
      BigDecimal deductibleAmount,
      BigDecimal liabilityLimit,
      boolean socialOnly,
      DeductibleRule.Target target,
      LiabilityForm form) {
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
        form,
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
   *
   * <p><b>拆成「形式 + 形状」两层</b>：{@link #validateConfigShape()} 是<b>配置面写入期</b>也能跑的子集（配置自相矛盾），
   * 责任形式的检查留在这里——定额给付/津贴给付型<b>允许配置</b>（业务要能建档），只是不能自动理算。
   */
  public void validate() {
    if (form != LiabilityForm.REIMBURSEMENT) {
      throw RuleGap.of(
          RuleGap.Code.LIABILITY_FORM_UNSUPPORTED, "责任形式 %s 在 V1 无规则（Q-11：定额给付型/津贴给付型整章空白）", form);
    }
    validateConfigShape();
  }

  /**
   * 配置形状自检：<b>只看配置本身是否自相矛盾</b>，不看责任形式，也不依赖任何案情事实。
   *
   * <p><b>为什么这一层必须能被配置面调用</b>：这些形态（配了日限额没配口径、开了白名单却没给名单、共保代码/先赔后赔、
   * 外部控额）在库里<b>看不出异常</b>，只有等到某个赔案走到理算才炸。而配置错了以后，后果是"这一整类赔案都算不出钱"——
   * 越晚发现代价越大。写入期就拒绝，等于把发现时点从"客户来报销"提前到"配置岗点保存"。
   *
   * <p>与 {@link #validate()} 共用同一段实现，不允许两处各写一份：配置面放行了、理算再拒的"两套判据"会让运营 看着保存成功的配置却永远赔不出钱。
   */
  public void validateConfigShape() {
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

  /** 是否配置了费用类别规则（空 ⇒ 引擎走"不拆类别"的老路径，输出与引入前逐字一致）。 */
  public boolean hasFeeRules() {
    return !feeRules.isEmpty();
  }

  /** 取某费用类别的规则；未配置该类别返回 {@code null}（调用方据此回落责任统一比例）。 */
  public FeeCategoryRule feeRuleOf(String feeCode) {
    if (feeCode == null || feeCode.isBlank() || feeRules.isEmpty()) {
      return null;
    }
    String code = feeCode.trim();
    for (FeeCategoryRule rule : feeRules) {
      if (rule.feeCode().equals(code)) {
        return rule;
      }
    }
    return null;
  }

  /** 责任额度是否封顶。 */
  public boolean liabilityCapped() {
    return !limits.liabilityUncapped();
  }
}
