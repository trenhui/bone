package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.gateway.ReviewPolicy;
import com.bone.aidoc.domain.model.ConfidenceSource;
import com.bone.aidoc.domain.model.CrossCheck;
import com.bone.aidoc.domain.model.FieldReviewContext;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ReviewDecision;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.RiskLevel;
import com.bone.aidoc.domain.model.RiskThreshold;
import com.bone.aidoc.domain.model.ValidationStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Phase-6 fail-open 回归钉子：每条用例都对应一条曾真实存在的「静默自动放行」路径。
 *
 * <p>修复前的行为：① 空白值 + NOT_APPLICABLE 在置信度/质量分/风险等级之前直接短路 AUTO_PASS； ② conf=5.0 满足 {@code 5.0 >=
 * 0.99}；③ 质量分越界无校验；④ 非 OCR 来源置信度共用 OCR 阈值； ⑤ 缺等级阈值直接 NPE。这些都是资金场景最危险的 fail-open，故逐条钉死。
 *
 * <p>同时保留正向对照：合法关键字段仍必须能 AUTO_PASS，避免把闸门收紧成「一律人工」的假安全。
 */
class ReviewPolicySafetyTest {

  private final DefaultReviewPolicy policy = new DefaultReviewPolicy();

  private static OcrFieldEvidence evidence(
      String value, double conf, ConfidenceSource source, ValidationStatus status) {
    return new OcrFieldEvidence(
        "total_amount", value, null, 1, null, "stub", conf, source, status, ReviewStatus.PENDING);
  }

  private static OcrFieldEvidence good() {
    return evidence("100.00", 0.999, ConfidenceSource.OCR_ENGINE, ValidationStatus.PASSED);
  }

  private static FieldReviewContext ctx(OcrFieldEvidence f, double q, RiskLevel risk) {
    return new FieldReviewContext(f, q, risk, CrossCheck.NOT_RUN);
  }

  @Test
  void blank_critical_value_is_never_auto_passed() {
    OcrFieldEvidence f =
        evidence("", 0.999, ConfidenceSource.OCR_ENGINE, ValidationStatus.NOT_APPLICABLE);
    var outcome = policy.decideWithReason(ctx(f, 0.99, RiskLevel.CRITICAL));
    assertThat(outcome.decision()).isEqualTo(ReviewDecision.MANUAL_REVIEW);
    assertThat(outcome.reason()).contains("[值]");
  }

  @Test
  void blank_value_is_not_auto_passed_at_any_risk_level() {
    for (RiskLevel risk : RiskLevel.values()) {
      OcrFieldEvidence f =
          evidence("  ", 0.999, ConfidenceSource.OCR_ENGINE, ValidationStatus.NOT_APPLICABLE);
      assertThat(policy.decide(ctx(f, 0.99, risk))).isNotEqualTo(ReviewDecision.AUTO_PASS);
    }
    assertThat(
            policy.decide(
                ctx(
                    evidence("", 0.999, ConfidenceSource.OCR_ENGINE, ValidationStatus.PASSED),
                    0.99,
                    RiskLevel.HIGH)))
        .isEqualTo(ReviewDecision.MANUAL_REVIEW);
    assertThat(
            policy.decide(
                ctx(
                    evidence("", 0.999, ConfidenceSource.OCR_ENGINE, ValidationStatus.PASSED),
                    Double.NaN,
                    RiskLevel.LOW)))
        .isEqualTo(ReviewDecision.SECONDARY_RECOGNIZE);
  }

  @Test
  void out_of_range_confidence_is_never_auto_passed() {
    for (double conf : new double[] {5.0, 1.01, -0.1, Double.NaN, Double.POSITIVE_INFINITY}) {
      OcrFieldEvidence f =
          evidence("100.00", conf, ConfidenceSource.OCR_ENGINE, ValidationStatus.PASSED);
      var outcome = policy.decideWithReason(ctx(f, 0.99, RiskLevel.CRITICAL));
      assertThat(outcome.decision()).isEqualTo(ReviewDecision.MANUAL_REVIEW);
      assertThat(outcome.reason()).contains("[L1]");
    }
  }

  @Test
  void out_of_range_quality_is_never_auto_passed() {
    for (double q : new double[] {-0.1, 1.5, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      assertThat(policy.decide(ctx(good(), q, RiskLevel.CRITICAL)))
          .isEqualTo(ReviewDecision.MANUAL_REVIEW);
    }
  }

  @Test
  void nan_quality_remains_the_documented_unavailable_signal() {
    assertThat(policy.decide(ctx(good(), Double.NaN, RiskLevel.CRITICAL)))
        .isEqualTo(ReviewDecision.SECONDARY_RECOGNIZE);
  }

  @Test
  void non_ocr_confidence_source_is_never_auto_passed() {
    for (ConfidenceSource source :
        new ConfidenceSource[] {ConfidenceSource.QUALITY_MODEL, ConfidenceSource.CALIBRATED}) {
      OcrFieldEvidence f = evidence("100.00", 0.999, source, ValidationStatus.PASSED);
      var outcome = policy.decideWithReason(ctx(f, 0.99, RiskLevel.CRITICAL));
      assertThat(outcome.decision()).isEqualTo(ReviewDecision.MANUAL_REVIEW);
      assertThat(outcome.reason()).contains("跨来源");
    }
  }

  @Test
  void missing_threshold_level_fails_closed_instead_of_throwing() {
    DefaultReviewPolicy onlyHigh =
        new DefaultReviewPolicy(Map.of(RiskLevel.HIGH, new RiskThreshold(0.90, 0.90, 0.80, 0.80)));
    assertThat(onlyHigh.decide(ctx(good(), 0.99, RiskLevel.CRITICAL)))
        .isEqualTo(ReviewDecision.MANUAL_REVIEW);
  }

  @Test
  void legitimate_critical_field_still_auto_passes() {
    var outcome = policy.decideWithReason(ctx(good(), 0.99, RiskLevel.CRITICAL));
    assertThat(outcome.decision()).isEqualTo(ReviewDecision.AUTO_PASS);
    assertThat(outcome.reason()).contains("autoPassConfidence", "autoPassQuality");
  }

  @Test
  void decide_delegates_to_decide_with_reason() {
    var c = ctx(good(), 0.99, RiskLevel.CRITICAL);
    assertThat(policy.decide(c)).isEqualTo(policy.decideWithReason(c).decision());
  }

  @Test
  void custom_policy_without_reason_override_still_gets_non_blank_reason() {
    ReviewPolicy custom = c -> ReviewDecision.AUTO_PASS;
    var outcome = custom.decideWithReason(ctx(good(), 0.99, RiskLevel.CRITICAL));
    assertThat(outcome.decision()).isEqualTo(ReviewDecision.AUTO_PASS);
    assertThat(outcome.reason()).isNotBlank();
  }
}
