package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bone.aidoc.domain.model.ConfidenceSource;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.QualityReport;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.ValidationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 量纲隔离钉子：质量分不得包含绝对 OCR 置信度。
 *
 * <p>修复前 {@code W_CONF * conf} 让 L1 信号同时以 conf 与 q 两种身份进入 {@code DefaultReviewPolicy}，违反 ADR-0046
 * §四「三者不可混为一谈」。本测试用「相对位置完全相同、 整体置信度成比例平移」的两组字段：隔离后质量分应完全相同，含绝对 conf 时必然不同。
 */
class HeuristicQualitySourceSeparationTest {

  private static OcrFieldEvidence field(String name, double conf) {
    return new OcrFieldEvidence(
        name,
        "v",
        null,
        1,
        null,
        "eng",
        conf,
        ConfidenceSource.OCR_ENGINE,
        ValidationStatus.PASSED,
        ReviewStatus.PENDING);
  }

  @Test
  void absolute_confidence_shift_does_not_change_quality_score() {
    List<OcrFieldEvidence> high = List.of(field("a", 0.8), field("b", 0.4));
    List<OcrFieldEvidence> low = List.of(field("a", 0.4), field("b", 0.2));
    var evaluator = new HeuristicOcrQualityEvaluator();
    QualityReport qHigh = evaluator.evaluate(high, OcrDocType.INVOICE, "ref");
    QualityReport qLow = evaluator.evaluate(low, OcrDocType.INVOICE, "ref");

    assertThat(qHigh.scoreFor("a")).isCloseTo(qLow.scoreFor("a"), within(1e-12));
    assertThat(qHigh.scoreFor("b")).isCloseTo(qLow.scoreFor("b"), within(1e-12));
  }

  @Test
  void relative_outlier_still_scores_lower() {
    QualityReport q =
        new HeuristicOcrQualityEvaluator()
            .evaluate(List.of(field("a", 0.8), field("b", 0.4)), OcrDocType.INVOICE, "ref");
    assertThat(q.scoreFor("b")).isLessThan(q.scoreFor("a"));
  }
}
