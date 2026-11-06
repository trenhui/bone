package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.ConfidenceSource;
import com.bone.aidoc.domain.model.FieldReviewContext;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ReviewDecision;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.RiskLevel;
import com.bone.aidoc.domain.model.ValidationStatus;
import org.junit.jupiter.api.Test;

/** {@link DefaultReviewPolicy} 的 L1~L5 组合路由断言（ADR-0046 §四：字段级、按风险分级、禁全局阈值）。 */
class DefaultReviewPolicyTest {

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
        ReviewStatus.PENDING);
  }

  private FieldReviewContext ctx(OcrFieldEvidence f, double quality, RiskLevel risk) {
    return new FieldReviewContext(f, quality, risk);
  }

  @Test
  void critical_without_quality_forces_secondary_not_auto_pass() {
    OcrFieldEvidence f = field(0.999, ValidationStatus.PASSED);
    assertThat(policy.decide(ctx(f, Double.NaN, RiskLevel.CRITICAL)))
        .isEqualTo(ReviewDecision.SECONDARY_RECOGNIZE);
  }

  @Test
  void critical_high_conf_and_quality_auto_pass() {
    OcrFieldEvidence f = field(0.999, ValidationStatus.PASSED);
    assertThat(policy.decide(ctx(f, 0.95, RiskLevel.CRITICAL))).isEqualTo(ReviewDecision.AUTO_PASS);
  }

  @Test
  void failed_rule_on_critical_goes_manual() {
    OcrFieldEvidence f = field(0.999, ValidationStatus.FAILED);
    assertThat(policy.decide(ctx(f, 0.99, RiskLevel.CRITICAL)))
        .isEqualTo(ReviewDecision.MANUAL_REVIEW);
  }

  @Test
  void low_field_high_conf_auto_pass() {
    OcrFieldEvidence f = field(0.99, ValidationStatus.PASSED);
    assertThat(policy.decide(ctx(f, Double.NaN, RiskLevel.LOW)))
        .isEqualTo(ReviewDecision.AUTO_PASS);
  }

  @Test
  void high_field_low_conf_goes_manual() {
    OcrFieldEvidence f = field(0.50, ValidationStatus.PASSED);
    assertThat(policy.decide(ctx(f, Double.NaN, RiskLevel.HIGH)))
        .isEqualTo(ReviewDecision.MANUAL_REVIEW);
  }

  @Test
  void medium_field_mid_conf_goes_secondary() {
    // 0.85 介于 manualThreshold(0.80) 与 autoPassThreshold(0.98) 之间，无质量分 → 二次识别
    OcrFieldEvidence f = field(0.85, ValidationStatus.PASSED);
    assertThat(policy.decide(ctx(f, Double.NaN, RiskLevel.MEDIUM)))
        .isEqualTo(ReviewDecision.SECONDARY_RECOGNIZE);
  }
}
