package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.ReviewPolicy;
import com.bone.aidoc.domain.model.ConfidenceSource;
import com.bone.aidoc.domain.model.CrossCheck;
import com.bone.aidoc.domain.model.FieldReviewContext;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ReviewDecision;
import com.bone.aidoc.domain.model.ReviewOutcome;
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
 *   <li>多模型 MISMATCH → 直接 {@code MANUAL_REVIEW}（不一致必须升级）。
 *   <li>CRITICAL 字段在无质量确认时 → {@code SECONDARY_RECOGNIZE}。
 *   <li>阈值由构造注入（{@link #defaultThresholds()} 为骨架值），须由真实标注样本标定后覆盖。
 *   <li><b>fail-closed</b>：空白关键字段、非法置信度/质量分、非 OCR 来源置信度、缺等级阈值， 一律不得自动放行——宁可转人工，也不能静默放过错误金额。
 * </ul>
 *
 * <p>每个分支均通过 {@link #decideWithReason} 给出命中闸门与阈值快照，由编排层写入审计轨迹。
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
    return decideWithReason(ctx).decision();
  }

  @Override
  public ReviewOutcome decideWithReason(FieldReviewContext ctx) {
    OcrFieldEvidence field = ctx.field();
    RiskLevel risk = ctx.riskLevel();
    RiskThreshold t = thresholds.get(risk);

    // L2：确定性规则失败 → 人工复核（先于任何模型判断）
    if (field.validationStatus().isFailed()) {
      return ReviewOutcome.manual("[L2] 确定性规则校验失败，禁止自动放行");
    }
    // L3'：多模型不一致 → 人工复核
    if (ctx.crossCheck() == CrossCheck.MISMATCH) {
      return ReviewOutcome.manual("[L3'] 多模型交叉不一致，禁止自动放行");
    }
    // 缺该风险等级的标定阈值 → fail-closed（不得借用其它等级阈值，也不得 NPE）
    if (t == null) {
      return ReviewOutcome.manual("[配置] 风险等级 " + risk + " 无标定阈值，fail-closed 转人工复核");
    }
    double conf = field.confidence();
    if (!isValidScore(conf)) {
      return ReviewOutcome.manual("[L1] OCR 置信度非法（" + conf + "，须为 [0,1] 有限值），禁止自动放行");
    }
    // 置信度来源：阈值均按 OCR 引擎置信度标定，跨来源比较等于把 Jev 分数当 OCR 置信度用
    if (field.confidenceSource() != ConfidenceSource.OCR_ENGINE) {
      return ReviewOutcome.manual(
          "[来源] 置信度来源为 "
              + field.confidenceSource()
              + "，当前 risk-thresholds 仅按 OCR_ENGINE 标定，禁止跨来源比较；须按该来源单独标定");
    }
    // 空白识别值：字段已在证据链中却无内容，绝非「可选字段未出现」。
    // 真正未出现的必填字段由 OcrFieldEvidence.missing 生成，其 FAILED 已在上方拦截。
    if (isBlank(field.value())) {
      return (risk == RiskLevel.CRITICAL || risk == RiskLevel.HIGH)
          ? ReviewOutcome.manual("[值] 关键字段 " + field.fieldName() + " 识别值为空，禁止自动放行")
          : ReviewOutcome.secondary("[值] 字段 " + field.fieldName() + " 识别值为空，转二次识别");
    }
    double q = ctx.qualityScore();
    if (q < 0.0 || q > 1.0 || Double.isInfinite(q)) {
      return ReviewOutcome.manual("[L3] 质量分非法（" + q + "，须为 [0,1] 或 NaN 表示不可用），禁止自动放行");
    }
    boolean qualityAvailable = !Double.isNaN(q);

    // CRITICAL 字段缺少质量确认 → 二次识别（不得仅凭 OCR 置信度放行）
    if (risk == RiskLevel.CRITICAL && !qualityAvailable) {
      return ReviewOutcome.secondary("[L3] CRITICAL 缺质量确认，不得仅凭 OCR 置信度自动放行");
    }
    if (computeAutoPass(risk, conf, q, qualityAvailable, t)) {
      return ReviewOutcome.autoPass(
          "[放行] "
              + risk
              + " conf="
              + conf
              + " q="
              + (qualityAvailable ? q : "N/A")
              + " 达到 autoPassConfidence="
              + t.autoPassConfidence()
              + " autoPassQuality="
              + t.autoPassQuality());
    }
    if (conf < t.manualConfidence() || (qualityAvailable && q < t.manualQuality())) {
      return ReviewOutcome.manual(
          "[阈值] "
              + risk
              + " 低于人工复核门槛 manualConfidence="
              + t.manualConfidence()
              + " manualQuality="
              + t.manualQuality());
    }
    return ReviewOutcome.secondary("[阈值] " + risk + " 未达自动通过门槛，转二次识别");
  }

  /** 置信度/质量分必须是 [0,1] 内的有限值；NaN 只允许作为「质量分不可用」的约定信号。 */
  private static boolean isValidScore(double v) {
    return !Double.isNaN(v) && !Double.isInfinite(v) && v >= 0.0 && v <= 1.0;
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
