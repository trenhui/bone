package com.bone.claim.domain.model.adjustment.engine;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 免赔规则（V1 §6 全 6 维）。
 *
 * <p><b>为什么 6 维一个都不能省</b>：这 6 维每换一个取值，<b>同一个免赔额算出的赔付就不同</b>，且差异方向不一致：
 *
 * <table>
 *   <caption>维度的作用点</caption>
 *   <tr><th>维度<th>错配后果
 *   <tr><td>类型（绝对/相对）<td>相对免赔写成绝对 ⇒ 达到门槛的客户被白扣一笔（系统性少赔）
 *   <tr><td>对象（理算金额/发票费用）<td>"折后减固定额"与"减固定额后再打折"不是一回事，金额越大差越大
 *   <tr><td>粒度（责任级/费用类型级）<td>按费用类型配了免赔却按责任级扣 ⇒ 三类费用只扣一次
 *   <tr><td>方式（年度/次/天数）<td>年度免赔按案扣 ⇒ 第 2 案起每案重扣满额
 *   <tr><td>形式（金额/比例/天数）<td>比例免赔按固定额扣 ⇒ 小额案多扣、大额案少扣
 *   <tr><td>抵扣（医保/三方是否可抵免赔）<td>同一份责任三种口径，首案免赔扣减就不同
 * </table>
 *
 * <p><b>默认值只在一处</b>：{@link #defaults()} 按 §6.1 表格给出"绝对 / 按年度 / 免赔金额 / 医保三方均可抵扣 / 理算金额 / 责任级 /
 * 无免赔"。除此之外<b>没有任何隐式默认</b>——尤其是"没配就按 90%"这类回落，在本文档里视为缺陷而非便利。
 */
public record DeductibleRule(
    boolean enabled,
    Kind kind,
    Basis basis,
    Form form,
    Offset offset,
    Target target,
    Granularity granularity,
    BigDecimal amount,
    BigDecimal ratio,
    int days,
    Map<FeePools.Bucket, BigDecimal> perBucketAmount) {

  /** 免赔类型（§6.1）：绝对总是扣，相对达到门槛即全免。 */
  public enum Kind {
    /** 绝对免赔：只要可赔金额 > 剩余免赔，就扣掉剩余免赔。 */
    ABSOLUTE,
    /** 相对免赔：达到门槛则<b>一分不减</b>（I5 跳档），未达门槛则全免赔。 */
    RELATIVE
  }

  /** 免赔方式（§6.4）。 */
  public enum Basis {
    /** 按年度：累计键 = 保单 + 主被保险人 + 责任 + 保单年度（<b>不是</b> claim_id）。 */
    ANNUAL,
    /** 按次：每案独立，不跨案累计。 */
    PER_CLAIM,
    /** 按天数：津贴型专用，费用补偿型不适用。 */
    PER_DAY
  }

  /** 免赔形式（§6.5）。 */
  public enum Form {
    /** 免赔金额（固定额度）。 */
    AMOUNT,
    /** 免赔比例：免赔额 = 基数 × 比例。 */
    RATIO,
    /** 免赔天数（津贴型）。 */
    DAYS
  }

  /** 免赔抵扣口径（§6.6）：决定"已用免赔额"如何累计。 */
  public enum Offset {
    /** 医保及三方赔付均可抵扣：已用 = 历次已用 + 医保赔付 + 三方赔付。 */
    MEDICAL_AND_THIRD_PARTY,
    /** 均不可抵扣：已用 = 历次已用。 */
    NONE,
    /** 医保不可、三方可抵扣：已用 = 历次已用 + 三方赔付。 */
    THIRD_PARTY_ONLY
  }

  /** 免赔对象（§6.3）：决定"先理算还是先扣免赔"。 */
  public enum Target {
    /** 理算金额（默认）：先乘比例，再减剩余免赔。 */
    CALCULATED_AMOUNT,
    /** 发票费用：先减剩余免赔，再乘比例。 */
    INVOICE_AMOUNT
  }

  /** 免赔粒度（§6.7 第 6 维）：决定免赔是按责任整体扣还是按费用类型各扣。 */
  public enum Granularity {
    /** 责任级：各费用类型理算金额<b>相加后</b>再扣减免赔。 */
    LIABILITY,
    /** 费用类型级：各费用类型<b>各自</b>扣对应免赔后求和。 */
    FEE_TYPE
  }

  public DeductibleRule {
    amount = Money.nz(amount);
    ratio = Money.nz(ratio);
    // EnumMap 在空 map 上会抛 "Specified map is empty"，且此处并不需要枚举序（口径顺序由调用方给定）
    perBucketAmount = perBucketAmount == null ? Map.of() : Map.copyOf(perBucketAmount);
  }

  /** 无免赔（§6.1 默认）。 */
  public static DeductibleRule none() {
    return new DeductibleRule(
        false,
        Kind.ABSOLUTE,
        Basis.ANNUAL,
        Form.AMOUNT,
        Offset.MEDICAL_AND_THIRD_PARTY,
        Target.CALCULATED_AMOUNT,
        Granularity.LIABILITY,
        Money.ZERO,
        Money.ZERO,
        0,
        Map.of());
  }

  /** 年度绝对免赔金额（旧责任配置 {@code liability_deduct} 的等价映射）。 */
  public static DeductibleRule annualAbsolute(BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      return none();
    }
    return new DeductibleRule(
        true,
        Kind.ABSOLUTE,
        Basis.ANNUAL,
        Form.AMOUNT,
        Offset.MEDICAL_AND_THIRD_PARTY,
        Target.CALCULATED_AMOUNT,
        Granularity.LIABILITY,
        amount,
        Money.ZERO,
        0,
        Map.of());
  }

  /** 相对免赔（年度、按理算金额）：用于 I5 跳档场景。 */
  public static DeductibleRule relativeAnnual(BigDecimal amount) {
    return new DeductibleRule(
        true,
        Kind.RELATIVE,
        Basis.ANNUAL,
        Form.AMOUNT,
        Offset.MEDICAL_AND_THIRD_PARTY,
        Target.CALCULATED_AMOUNT,
        Granularity.LIABILITY,
        amount,
        Money.ZERO,
        0,
        Map.of());
  }

  /** 费用类型级免赔（§5.5 示例 A(3)）。 */
  public static DeductibleRule perFeeType(
      Map<FeePools.Bucket, BigDecimal> perBucket, Target target) {
    return new DeductibleRule(
        true,
        Kind.ABSOLUTE,
        Basis.ANNUAL,
        Form.AMOUNT,
        Offset.MEDICAL_AND_THIRD_PARTY,
        target == null ? Target.CALCULATED_AMOUNT : target,
        Granularity.FEE_TYPE,
        Money.ZERO,
        Money.ZERO,
        0,
        perBucket);
  }

  /** 是否为"无免赔"（未启用或以 0 额度配置——两者等价，不必让调用方分情况）。 */
  public boolean inactive() {
    return !enabled || (form == Form.AMOUNT && amount.signum() == 0 && perBucketAmount.isEmpty());
  }

  /** 某费用类型的免赔额（费用类型级）；未配置返回 0（该类型不设免赔是合法配置）。 */
  public BigDecimal amountOf(FeePools.Bucket bucket) {
    BigDecimal configured = perBucketAmount.get(bucket);
    return Money.nz(configured);
  }

  /**
   * 配置自检（装载期）。
   *
   * <p>三类组合在数学上不成立，必须在配置期拒绝：按天数免赔 + 费用补偿型、相对免赔 + 免赔比例（相对免赔比较的是金额门槛）、 费用类型级但一个额度都没配（等于"配了不生效"）。
   */
  public void validate() {
    if (inactive()) {
      return;
    }
    if (basis == Basis.PER_DAY || form == Form.DAYS) {
      throw RuleGap.of(RuleGap.Code.DEDUCTIBLE_CONFIG_INVALID, "免赔方式/形式为按天数：仅津贴给付型适用，费用补偿型（V1）不支持");
    }
    if (kind == Kind.RELATIVE && form == Form.RATIO) {
      throw RuleGap.of(
          RuleGap.Code.DEDUCTIBLE_CONFIG_INVALID, "相对免赔与免赔比例互斥：相对免赔需要确定的金额门槛，比例形式没有门槛可比");
    }
    if (form == Form.RATIO) {
      // 免赔比例要求"先按基数算出免赔额，再判断是否跨门槛"，其基数（发票费用 / 理算金额）与 target 组合
      // 有四种口径，V1 §6.5 只给了公式没给矩阵。此处显式拒绝而不是按 0 静默处理——静默的后果是
      // "配了比例免赔但一分没扣"，属最高危的配了不生效。
      throw RuleGap.of(
          RuleGap.Code.DEDUCTIBLE_CONFIG_INVALID, "免赔形式为「免赔比例」：基数与免赔对象的组合口径未定义（V1 §6.5），暂不自动理算");
    }
    if (granularity == Granularity.FEE_TYPE && perBucketAmount.isEmpty()) {
      throw RuleGap.of(RuleGap.Code.DEDUCTIBLE_CONFIG_INVALID, "免赔粒度为费用类型级但未配置任何费用类型额度（配了不生效）");
    }
  }
}
