package com.bone.aidoc.domain.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 阈值标定报告：按风险等级的结论 + 统计纪律告警 + 可直接使用的配置片段。 */
public record CalibrationReport(
    double targetFalseAcceptRate,
    int totalSamples,
    Map<RiskLevel, LevelCalibration> byLevel,
    List<String> warnings) {

  /** 无样本的空报告。 */
  public static CalibrationReport empty(double target) {
    return new CalibrationReport(target, 0, Map.of(), List.of("标定样本为空，无法产出任何阈值结论"));
  }

  /**
   * <b>全部四个</b>风险等级都有结论且都通过统计证明才为 true。
   *
   * <p>此前只要求「出现过的等级」全部证明，导致只喂单等级样本即可宣称全级别达标—— 属空集假绿。生产阈值是分级生效的，缺任一等级的证明就不能放行整套配置。
   */
  public boolean allLevelsProven() {
    if (byLevel.size() != RiskLevel.values().length) {
      return false;
    }
    return byLevel.values().stream().allMatch(LevelCalibration::statisticallyProven);
  }

  /**
   * 生成 Spring Boot YAML 配置片段。
   *
   * <p>Map 的键是枚举名，<b>必须大写</b>（Spring 用 {@code Enum.valueOf} 转换，小写会绑定失败）； 换成 .properties 时写作 {@code
   * bone.ai.document.trusted-ocr.risk-thresholds.CRITICAL.*}。
   */
  public String toConfigSnippet() {
    StringBuilder sb = new StringBuilder();
    sb.append("bone.ai.document.trusted-ocr.risk-thresholds:\n");
    byLevel.forEach(
        (level, c) -> {
          RiskThreshold t = c.suggestedThreshold();
          sb.append("  ")
              .append(level.name().toUpperCase(Locale.ROOT))
              .append(":\n")
              .append("    auto-pass-confidence: ")
              .append(fmt(t.autoPassConfidence()))
              .append('\n')
              .append("    auto-pass-quality: ")
              .append(fmt(t.autoPassQuality()))
              .append('\n')
              .append("    manual-confidence: ")
              .append(fmt(t.manualConfidence()))
              .append('\n')
              .append("    manual-quality: ")
              .append(fmt(t.manualQuality()))
              .append('\n');
        });
    return sb.toString();
  }

  private static String fmt(double v) {
    return String.format(Locale.ROOT, "%.3f", v);
  }
}
