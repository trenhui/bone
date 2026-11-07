package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.OcrQualityEvaluator;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.QualityReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型无关的启发式质量评估器（ADR-0046 L3 质量门的「无 Jev 模型」默认实现）。
 *
 * <p>在未接入 MiniCPM-OCR Jev 等训练质量模型时，用<b>可观测信号</b>合成字段级质量分（不重写原值）， 让 {@code DefaultReviewPolicy}
 * 的质量感知路由真正生效（否则 CRITICAL 字段因质量分缺失永远转二次识别）。
 *
 * <p>合成信号（权重见常量，合计为 1）：
 *
 * <ul>
 *   <li>相对置信度离散度（0.6）：OCR 置信度常整体虚高，故用「相对文档中位置信度」衡量可靠性—— 明显低于同文档其它字段者更可疑。<b>刻意不混入绝对 OCR 置信度</b>：L1
 *       已单独用 conf 与 阈值比较，若质量分再含 conf，同一信号会以两种量纲重复进入策略（ADR-0046 §四 禁止混用）；
 *   <li>证据定位覆盖（0.2）：具备有效 {@code bbox} 的字段更可信，缺坐标字段降级（关键字段 100% 覆盖是独立验收指标）；
 *   <li>规则校验状态（0.2）：PASSED 加分、FAILED 记 0（确定性规则优先于模型判断）；
 *   <li>空值惩罚（乘子 0.3，下限 0.1）：识别值为空无可信内容。
 * </ul>
 *
 * <p><b>定位声明</b>：本评估器是<b>代理信号</b>，非 Jev 类标定模型。真实质量模型（按票种/字段/风险用标注样本标定、AUROC 仅作筛选） 应作为基础设施 Bean 以
 * {@code @ConditionalOnMissingBean} 覆盖本实现；届时质量分来自模型推断而非启发式合成。
 */
public class HeuristicOcrQualityEvaluator implements OcrQualityEvaluator {

  /** 各信号权重（合计为 1）；不含绝对 OCR 置信度。 */
  private static final double W_DISPERSION = 0.6;

  private static final double W_COVERAGE = 0.2;
  private static final double W_VALIDATION = 0.2;

  /** 缺坐标字段的覆盖分（低于有坐标字段）。 */
  private static final double COVERAGE_NO_BBOX = 0.6;

  /** 空值惩罚乘子。 */
  private static final double BLANK_PENALTY = 0.3;

  /** 空值惩罚后下限。 */
  private static final double BLANK_FLOOR = 0.1;

  @Override
  public String evaluatorId() {
    return "heuristic";
  }

  @Override
  public QualityReport evaluate(
      List<OcrFieldEvidence> fields, OcrDocType docType, String sourceRef) {
    if (fields == null || fields.isEmpty()) {
      return new QualityReport(evaluatorId(), Map.of(), Double.NaN);
    }
    double medianConf = medianConfidence(fields);
    Map<String, Double> scores = new LinkedHashMap<>();
    double sum = 0.0;
    for (OcrFieldEvidence f : fields) {
      double q = scoreField(f, medianConf);
      scores.put(f.fieldName(), q);
      sum += q;
    }
    double overall = sum / fields.size();
    return new QualityReport(evaluatorId(), scores, overall);
  }

  private double scoreField(OcrFieldEvidence f, double medianConf) {
    // 相对置信度离散度：明显低于同文档中位数者更可疑
    double dispersion;
    if (medianConf <= 0.0) {
      dispersion = 1.0;
    } else if (f.confidence() >= medianConf) {
      dispersion = 1.0;
    } else {
      dispersion = 0.5 + 0.5 * (f.confidence() / medianConf);
    }

    // 证据定位覆盖
    double coverage = f.hasEvidenceLocation() ? 1.0 : COVERAGE_NO_BBOX;

    // 规则校验状态
    double validation =
        switch (f.validationStatus()) {
          case FAILED -> 0.0;
          case PASSED -> 1.0;
          case NOT_APPLICABLE, PENDING -> 0.7;
        };

    double base = W_DISPERSION * dispersion + W_COVERAGE * coverage + W_VALIDATION * validation;

    if (isBlank(f.value())) {
      base = Math.max(BLANK_FLOOR, base * BLANK_PENALTY);
    }
    return clamp01(base);
  }

  private static double medianConfidence(List<OcrFieldEvidence> fields) {
    List<Double> cs = new ArrayList<>();
    for (OcrFieldEvidence f : fields) {
      cs.add(f.confidence());
    }
    cs.sort(Double::compareTo);
    int n = cs.size();
    if (n == 0) {
      return 0.0;
    }
    if (n % 2 == 1) {
      return cs.get(n / 2);
    }
    return (cs.get(n / 2 - 1) + cs.get(n / 2)) / 2.0;
  }

  private static double clamp01(double v) {
    if (Double.isNaN(v)) {
      return 0.0;
    }
    return Math.max(0.0, Math.min(1.0, v));
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
