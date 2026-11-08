package com.bone.claim.domain.model.adjustment.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 赔付比例查找表（V1 §5 全族）。
 *
 * <p><b>为什么不能是一个 {@code BigDecimal}</b>：V1 §5.3 的配置截图是<b>全维笛卡尔展开</b>—— 承担费用类型 × 区分医保赔付 × 费用限额 ×
 * 赔案累计区间，每格一个百分比。 把这种规则压成一个标量，等于把"多维查表"退化成"全局一个比例"，条款里"1000 元以上部分赔 70%"这类约定会静默失效。
 *
 * <p><b>两种形态</b>（§5.1）：
 *
 * <ul>
 *   <li>同一比例 → {@link #uniform(BigDecimal)}，所有查询命中同一格；
 *   <li>不同比例 → {@link #of(List)}，按 {@link RatioSelector} 命中<b>唯一</b>一格。
 * </ul>
 *
 * <p><b>fail-closed 的两条硬规则</b>（§4.2 步 3）：
 *
 * <ol>
 *   <li><b>未命中不回落默认值</b>——没配就是没配，回落会让"配置缺失"变成"按猜的比例赔钱"；
 *   <li><b>命中多格视为配置错误</b>——两格都适用说明区间或维度重叠，此时挑哪一格都是错的。
 * </ol>
 */
public record PayRatioTable(String form, List<Entry> entries) {

  /** 同一比例形态的标识。 */
  public static final String FORM_UNIFORM = "SAME_RATIO";

  /** 不同比例形态的标识。 */
  public static final String FORM_MULTI = "DIFFERENT_RATIO";

  public PayRatioTable {
    entries = List.copyOf(entries);
  }

  /**
   * 一格配置。
   *
   * @param label 展示名（写进快照，便于人工复核"这一格是哪来的"）
   * @param selector 命中条件
   * @param percent 赔付比例（百分数，如 90）
   * @param feeLimit 该格独立的费用限额（§5.3：截图里每行都有费用限额输入框；{@code null} 或 0 = 不限）
   */
  public record Entry(
      String label, RatioSelector selector, BigDecimal percent, BigDecimal feeLimit) {

    public Entry {
      label = label == null ? "" : label;
      percent = Money.nz(percent);
    }

    /** 该格是否有独立费用限额（0 与 null 都表示"不限"，避免"限额 0 = 一分不赔"的歧义）。 */
    public boolean hasFeeLimit() {
      return feeLimit != null && feeLimit.signum() > 0;
    }
  }

  /**
   * 命中条件：{@code null} 维度表示"不限该维度"。
   *
   * <p>维度取自 §5.2 的 11 个因子中有工程落点的部分：承担费用类型、区分医保赔付、赔案累计区间、 发票总费用区间、医院等级、疾病类型。年龄/职业/性别等人员维度由 {@code
   * 适用对象} 在前置校验阶段处理，不在此重复表达。
   */
  public record RatioSelector(
      FeePools.Bucket bucket,
      Boolean medicalInsurancePaid,
      AmountRange accumulated,
      AmountRange invoiceTotal,
      String hospitalLevel,
      String diseaseType) {

    /** 是否命中查询。所有"非 null 维度"都必须相等 / 包含。 */
    public boolean matches(RatioQuery query) {
      if (bucket != null && bucket != query.bucket()) {
        return false;
      }
      if (medicalInsurancePaid != null
          && !medicalInsurancePaid.equals(query.medicalInsurancePaid())) {
        return false;
      }
      if (accumulated != null && !accumulated.contains(query.accumulated())) {
        return false;
      }
      if (invoiceTotal != null && !invoiceTotal.contains(query.invoiceTotal())) {
        return false;
      }
      if (hospitalLevel != null && !hospitalLevel.equals(query.hospitalLevel())) {
        return false;
      }
      return diseaseType == null || diseaseType.equals(query.diseaseType());
    }

    /** 维度指纹（用于重叠检测：指纹相同 + 区间相交 = 冲突）。 */
    String fingerprint() {
      // 区间维度单独比较，其余维度直接进指纹
      return bucket + "|" + medicalInsurancePaid + "|" + hospitalLevel + "|" + diseaseType;
    }

    /** 与另一条件是否可能同时命中同一查询（用于配置期重叠检测）。 */
    boolean mayOverlap(RatioSelector other) {
      if (!fingerprint().equals(other.fingerprint())) {
        return false;
      }
      return rangesMayOverlap(accumulated, other.accumulated)
          && rangesMayOverlap(invoiceTotal, other.invoiceTotal);
    }

    private static boolean rangesMayOverlap(AmountRange left, AmountRange right) {
      if (left == null || right == null) {
        return true; // 一方"不限区间"⇒ 与任何另一区间都可能同时命中
      }
      // 无上界按 +∞ 参与比较：直接判"任一无界即重叠"会把 [0,1000) 与 [2000,∞) 误报成冲突。
      BigDecimal unbounded = new BigDecimal("9E18");
      BigDecimal leftTo = left.unbounded() ? unbounded : left.to();
      BigDecimal rightTo = right.unbounded() ? unbounded : right.to();
      return left.from().compareTo(rightTo) < 0 && right.from().compareTo(leftTo) < 0;
    }
  }

  /**
   * 查表请求。
   *
   * @param bucket 承担费用类型
   * @param medicalInsurancePaid 是否经医保赔付（{@code null} = 未知，命中"不限"的格）
   * @param accumulated 赔案累计金额（历史 + 当前，见 {@link #accumulatedOf}）
   * @param invoiceTotal 发票总费用（用于"发票总费用分段区间"因子）
   * @param hospitalLevel 医院等级
   * @param diseaseType 疾病类型
   */
  public record RatioQuery(
      FeePools.Bucket bucket,
      Boolean medicalInsurancePaid,
      BigDecimal accumulated,
      BigDecimal invoiceTotal,
      String hospitalLevel,
      String diseaseType) {

    public RatioQuery {
      accumulated = Money.nz(accumulated);
      invoiceTotal = Money.nz(invoiceTotal);
    }

    /** 最简查询（仅费用类型 + 累计金额）。 */
    public static RatioQuery of(FeePools.Bucket bucket, BigDecimal accumulated) {
      return new RatioQuery(bucket, null, accumulated, Money.ZERO, null, null);
    }
  }

  /** 命中结果。 */
  public record Resolution(BigDecimal percent, BigDecimal feeLimit, String label) {

    public Resolution {
      percent = Money.nz(percent);
    }

    public boolean hasFeeLimit() {
      return feeLimit != null && feeLimit.signum() > 0;
    }
  }

  /** 同一比例形态（所有查询命中同一格）。 */
  public static PayRatioTable uniform(BigDecimal percent) {
    return new PayRatioTable(
        FORM_UNIFORM,
        List.of(
            new Entry(
                "统一比例", new RatioSelector(null, null, null, null, null, null), percent, null)));
  }

  /** 不同比例形态。 */
  public static PayRatioTable of(List<Entry> entries) {
    if (entries == null || entries.isEmpty()) {
      throw RuleGap.of(RuleGap.Code.RATIO_NOT_MATCHED, "赔付比例表为空：至少需要一格配置");
    }
    return new PayRatioTable(FORM_MULTI, entries);
  }

  /**
   * 查表。
   *
   * @throws RuleGap 未命中（{@link RuleGap.Code#RATIO_NOT_MATCHED}）或命中多格（{@link
   *     RuleGap.Code#RATIO_AMBIGUOUS}）
   */
  public Resolution lookup(RatioQuery query) {
    List<Entry> matched = new ArrayList<>();
    for (Entry entry : entries) {
      if (entry.selector().matches(query)) {
        matched.add(entry);
      }
    }
    if (matched.isEmpty()) {
      throw RuleGap.of(
          RuleGap.Code.RATIO_NOT_MATCHED,
          "赔付比例无适用格：bucket=%s, 医保赔付=%s, 累计=%s, 票额=%s, 医院等级=%s, 疾病=%s",
          query.bucket(),
          query.medicalInsurancePaid(),
          query.accumulated().toPlainString(),
          query.invoiceTotal().toPlainString(),
          query.hospitalLevel(),
          query.diseaseType());
    }
    if (matched.size() > 1) {
      throw RuleGap.of(
          RuleGap.Code.RATIO_AMBIGUOUS,
          "赔付比例命中 %d 格（区间/维度重叠）：%s",
          matched.size(),
          matched.stream().map(Entry::label).toList());
    }
    Entry hit = matched.get(0);
    return new Resolution(hit.percent(), hit.feeLimit(), hit.label());
  }

  /**
   * 配置期自检：区间两两不重叠、维度不冲突（I3）。
   *
   * <p>放在装载期而不是理赔期：理赔时才发现重叠，案子已经卡在流转中，而配置期报错只需改一行配置。
   */
  public void validate() {
    for (int i = 0; i < entries.size(); i++) {
      for (int j = i + 1; j < entries.size(); j++) {
        Entry left = entries.get(i);
        Entry right = entries.get(j);
        if (left.selector().mayOverlap(right.selector())) {
          throw RuleGap.of(
              RuleGap.Code.RATIO_AMBIGUOUS, "赔付比例区间重叠：%s 与 %s 可能同时命中", left.label(), right.label());
        }
      }
    }
    Set<String> labels = new HashSet<>();
    for (Entry entry : entries) {
      if (!labels.add(entry.label())) {
        throw RuleGap.of(RuleGap.Code.RATIO_AMBIGUOUS, "赔付比例格标签重复：%s（快照无法定位命中格）", entry.label());
      }
    }
  }

  /**
   * 累计区间定位口径（§5.4）：<b>历史累计 + 当前发票</b>。
   *
   * <p><b>这是引擎里唯一会改变"比例档"的输入</b>：用错区间，比例整体错一档，且错得很安静（金额看起来合理）。 故累计口径在此收口，不允许调用方各拼各的。
   *
   * @param historicalAccumulated 历史已完成案件的同类型累计（不含本赔案）
   * @param currentInvoice 当前发票的同类型金额
   */
  public static BigDecimal accumulatedOf(
      BigDecimal historicalAccumulated, BigDecimal currentInvoice) {
    return Money.round(Money.nz(historicalAccumulated).add(Money.nz(currentInvoice)));
  }

  /** 是否同一比例形态。 */
  public boolean uniformForm() {
    return FORM_UNIFORM.equals(form) || entries.size() == 1;
  }

  /** 配置里出现的独立费用限额集合（用于 7 层控额的第 ①② 层）。 */
  public List<BigDecimal> feeLimits() {
    return entries.stream()
        .filter(Entry::hasFeeLimit)
        .map(Entry::feeLimit)
        .filter(Objects::nonNull)
        .toList();
  }
}
