package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.ReviewPolicy;
import com.bone.aidoc.domain.model.CrossCheck;
import com.bone.aidoc.domain.model.FieldReviewContext;
import com.bone.aidoc.domain.model.ReviewDecision;
import com.bone.aidoc.domain.model.RiskLevel;
import com.bone.aidoc.domain.model.RiskThreshold;
import java.util.EnumMap;
import java.util.Map;

/**
 * 默认复核路由策略（ADR-0046 五层可信度 L1~L5 的最终裁决：字段级、按风险分级标定、禁全局阈值）。
 *
 * <p>阈值按 {@link RiskLevel} 分别持有（{@link RiskThreshold}），而非单一全局值。 组合信号：OCR
 * 置信度（L1）、规则校验状态（L2）、质量分（L3）、 多模型交叉（L3'）、风险等级（L4/L5 前置）。铁律落地：
 *
 * <ul>
 *   <li>规则校验 FAILED → 直接 {@code MANUAL_REVIEW}（确定性规则优先于模型判断）。
 *   <li>多模型 MISMATCH → 直接 {@code MANUAL_REVIEW}（不一致必须升级，即使两引擎一致也绝不因此自动通过）。
 *   <li>CRITICAL 字段在无质量确认时 → {@code SECONDARY_RECOGNIZE}（不得仅凭 OCR 置信度自动放行）。
 *   <li>阈值由构造注入（{@link #defaultThresholds()} 为骨架值），须由真实标注样本按票种/字段/风险标定后覆盖。
 * </ul>
 */
public class DefaultReviewPolicy implements ReviewPolicy {

  private final Map<RiskLevel, RiskThreshold> thresholds;

  /** 默认阈值（骨架初值，须按真实样本标定覆盖）。 */
  public DefaultReviewPolicy() {
    this(defaultThresholds());
  }

  /** 兼容旧扁平阈值配置：HIGH/MEDIUM/LOW 直接取扁平值，CRITICAL 在扁平值上施加更高下限。 */
  public DefaultReviewPolicy(
      double autoPassThreshold,
      double qualityAutoPassThreshold,
      double manualThreshold,
      double manualQualityThreshold) {
    this(
        ofFlat(
            autoPassThreshold, qualityAutoPassThreshold, manualThreshold, manualQualityThreshold));
  }

  /** 按风险等级显式标定阈值（推荐：生产由标注样本标定后注入）。 */
  public DefaultReviewPolicy(Map<RiskLevel, RiskThreshold> thresholds) {
    this.thresholds = new EnumMap<>(thresholds);
  }

  /** 骨架默认阈值（CRITICAL 严于其它等级）。 */
  public static Map<RiskLevel, RiskThreshold> defaultThresholds() {
    Map<RiskLevel, RiskThreshold> m = new EnumMap<>(RiskLevel.class);
    m.put(RiskLevel.CRITICAL, new RiskThreshold(0.99, 0.95, 0.90, 0.85));
    m.put(RiskLevel.HIGH, new RiskThreshold(0.98, 0.90, 0.80, 0.70));
    m.put(RiskLevel.MEDIUM, new RiskThreshold(0.95, 0.85, 0.70, 0.60));
    m.put(RiskLevel.LOW, new RiskThreshold(0.90, 0.80, 0.60, 0.50));
    return m;
  }

  private static Map<RiskLevel, RiskThreshold> ofFlat(
      double autoPass, double qAutoPass, double manual, double manualQuality) {
    Map<RiskLevel, RiskThreshold> m = defaultThresholds();
    m.put(
        RiskLevel.CRITICAL,
        new RiskThreshold(
            Math.max(autoPass, 0.99),
            Math.max(qAutoPass, 0.95),
            Math.max(manual, 0.90),
            Math.max(manualQuality, 0.85)));
    m.put(RiskLevel.HIGH, new RiskThreshold(autoPass, qAutoPass, manual, manualQuality));
    m.put(RiskLevel.MEDIUM, new RiskThreshold(autoPass, qAutoPass, manual, manualQuality));
    m.put(RiskLevel.LOW, new RiskThreshold(autoPass, qAutoPass, manual, manualQuality));
    return m;
  }

  @Override
  public ReviewDecision decide(FieldReviewContext ctx) {
    var field = ctx.field();
    RiskLevel risk = ctx.riskLevel();
    RiskThreshold t = thresholds.get(risk);

    // L2：规则校验失败 → 直接人工复核（确定性规则优先于模型判断）
    if (field.validationStatus().isFailed()) {
      return ReviewDecision.MANUAL_REVIEW;
    }
    // L3'：多模型不一致 → 直接人工复核（不一致必须升级，不因一致而自动通过）
    if (ctx.crossCheck() == CrossCheck.MISMATCH) {
      return ReviewDecision.MANUAL_REVIEW;
    }
    // 不适用且缺值的字段视为通过（可选字段未出现）
    if (field.validationStatus().isNotApplicable() && isBlank(field.value())) {
      return ReviewDecision.AUTO_PASS;
    }

    double conf = field.confidence();
    double q = ctx.qualityScore();
    boolean qualityAvailable = !Double.isNaN(q);

    // CRITICAL 字段缺少质量确认 → 二次识别（不得仅凭 OCR 置信度放行）
    if (risk == RiskLevel.CRITICAL && !qualityAvailable) {
      return ReviewDecision.SECONDARY_RECOGNIZE;
    }

    boolean autoPass = computeAutoPass(risk, conf, q, qualityAvailable, t);
    if (!autoPass) {
      if (conf < t.manualConfidence() || (qualityAvailable && q < t.manualQuality())) {
        return ReviewDecision.MANUAL_REVIEW;
      }
      return ReviewDecision.SECONDARY_RECOGNIZE;
    }
    return ReviewDecision.AUTO_PASS;
  }

  private boolean computeAutoPass(
      RiskLevel risk, double conf, double q, boolean qualityAvailable, RiskThreshold t) {
    if (risk == RiskLevel.CRITICAL) {
      // 到此分支时质量分必可用
      return conf >= t.autoPassConfidence() && q >= t.autoPassQuality();
    }
    if (risk == RiskLevel.HIGH) {
      return conf >= t.autoPassConfidence() && (!qualityAvailable || q >= t.autoPassQuality());
    }
    return conf >= t.autoPassConfidence();
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
