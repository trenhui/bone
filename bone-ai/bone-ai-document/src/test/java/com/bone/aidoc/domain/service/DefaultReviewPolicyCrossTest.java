package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.ConfidenceSource;
import com.bone.aidoc.domain.model.CrossCheck;
import com.bone.aidoc.domain.model.FieldReviewContext;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ReviewDecision;
import com.bone.aidoc.domain.model.RiskLevel;
import com.bone.aidoc.domain.model.ValidationStatus;
import org.junit.jupiter.api.Test;

/** {@link DefaultReviewPolicy} 交叉信号与分级阈值断言。 */
class DefaultReviewPolicyCrossTest {

  private final DefaultReviewPolicy policy = new DefaultReviewPolicy();

  private OcrFieldEvidence field(double confidence, ValidationStatus vs) {
    return new OcrFieldEvidence(
        "total_amount",
        "100.00",
        null,
        1,
        null,
        "stub",
        confidence,
        ConfidenceSource.OCR_ENGINE,
        vs,
        com.bone.aidoc.domain.model.ReviewStatus.PENDING);
  }

  @Test
  void cross_mismatch_forces_manual_regardless_of_confidence() {
    // CRITICAL + 极高置信度 + 交叉分歧 → 仍人工复核
    OcrFieldEvidence f = field(0.999, ValidationStatus.PASSED);
    FieldReviewContext ctx =
        new FieldReviewContext(f, Double.NaN, RiskLevel.CRITICAL, CrossCheck.MISMATCH);
    assertThat(policy.decide(ctx)).isEqualTo(ReviewDecision.MANUAL_REVIEW);
  }

  @Test
  void cross_match_does_not_auto_pass_critical_without_quality() {
    // 一致 ≠ 正确：CRITICAL 无质量分即便交叉一致仍转二次识别
    OcrFieldEvidence f = field(0.999, ValidationStatus.PASSED);
    FieldReviewContext ctx =
        new FieldReviewContext(f, Double.NaN, RiskLevel.CRITICAL, CrossCheck.MATCH);
    assertThat(policy.decide(ctx)).isEqualTo(ReviewDecision.SECONDARY_RECOGNIZE);
  }
}
