package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.aidoc.domain.model.CalibrationReport;
import com.bone.aidoc.domain.model.CalibrationSample;
import com.bone.aidoc.domain.model.ConfidenceSource;
import com.bone.aidoc.domain.model.CrossCheck;
import com.bone.aidoc.domain.model.FieldReviewContext;
import com.bone.aidoc.domain.model.LevelCalibration;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ReviewDecision;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.RiskLevel;
import com.bone.aidoc.domain.model.ValidationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 阈值标定：统计纪律、口径一致性、错误放行率优先。 */
class ThresholdCalibratorTest {

  private static List<CalibrationSample> clean(int n, RiskLevel level, double conf, double q) {
    List<CalibrationSample> list = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      list.add(CalibrationSample.of(level, conf, q, true));
    }
    return list;
  }

  @Test
  void zero_errors_in_small_sample_is_not_statistically_proven() {
    CalibrationReport report =
        new ThresholdCalibrator().calibrate(clean(100, RiskLevel.HIGH, 0.999, 0.99));
    LevelCalibration c = report.byLevel().get(RiskLevel.HIGH);
    assertThat(c.falseAccepts()).isZero();
    assertThat(c.statisticallyProven()).isFalse();
    assertThat(c.wilsonUpperBound()).isGreaterThan(report.targetFalseAcceptRate());
    assertThat(c.minSamplesRequired()).isGreaterThan(30_000);
    assertThat(report.warnings()).anyMatch(w -> w.contains("禁止作为生产阈值"));
  }

  @Test
  void relaxed_target_with_enough_clean_samples_is_proven() {
    CalibrationReport report =
        new ThresholdCalibrator(0.01).calibrate(clean(500, RiskLevel.HIGH, 0.999, 0.99));
    LevelCalibration c = report.byLevel().get(RiskLevel.HIGH);
    assertThat(c.statisticallyProven()).isTrue();
    assertThat(c.autoPassed()).isEqualTo(500);
    assertThat(c.suggestedThreshold().autoPassConfidence()).isLessThanOrEqualTo(0.999);
    assertThat(report.allLevelsProven()).isTrue();
  }

  @Test
  void error_hidden_in_high_confidence_forces_higher_threshold() {
    List<CalibrationSample> samples = new ArrayList<>(clean(100, RiskLevel.HIGH, 0.99, 0.99));
    samples.add(CalibrationSample.of(RiskLevel.HIGH, 0.95, 0.99, false));
    CalibrationReport report = new ThresholdCalibrator(0.05).calibrate(samples);
    LevelCalibration c = report.byLevel().get(RiskLevel.HIGH);

    assertThat(c.falseAccepts()).isZero();
    assertThat(c.autoPassed()).isEqualTo(100);
    assertThat(c.suggestedThreshold().autoPassConfidence()).isGreaterThan(0.95);
    assertThat(c.noSafeThreshold()).isFalse();
    assertThat(c.statisticallyProven()).isTrue();
  }

  @Test
  void all_samples_wrong_reports_no_safe_threshold() {
    List<CalibrationSample> samples = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      samples.add(CalibrationSample.of(RiskLevel.CRITICAL, 0.999, 0.99, false));
    }
    CalibrationReport report = new ThresholdCalibrator(0.01).calibrate(samples);
    LevelCalibration c = report.byLevel().get(RiskLevel.CRITICAL);
    assertThat(c.noSafeThreshold()).isTrue();
    assertThat(c.statisticallyProven()).isFalse();
    assertThat(c.suggestedThreshold().autoPassConfidence()).isEqualTo(1.0);
    assertThat(report.warnings()).anyMatch(w -> w.contains("强制人工复核"));
  }

  @Test
  void wilson_upper_decreases_with_sample_size_and_zero_sample_is_max() {
    assertThat(ThresholdCalibrator.wilsonUpperBound(0, 0)).isEqualTo(1.0);
    assertThat(ThresholdCalibrator.wilsonUpperBound(0, 10))
        .isGreaterThan(ThresholdCalibrator.wilsonUpperBound(0, 1000));
    assertThat(ThresholdCalibrator.wilsonUpperBound(5, 100))
        .isGreaterThan(ThresholdCalibrator.wilsonUpperBound(0, 100));
    int required = ThresholdCalibrator.minimumSampleSizeForZeroErrors(0.0001);
    assertThat(ThresholdCalibrator.wilsonUpperBound(0, required)).isLessThanOrEqualTo(0.0001);
    assertThat(ThresholdCalibrator.wilsonUpperBound(0, required - 1)).isGreaterThan(0.0001);
  }

  @Test
  void calibration_uses_exactly_the_runtime_policy() {
    CalibrationReport report =
        new ThresholdCalibrator(0.05).calibrate(clean(200, RiskLevel.HIGH, 0.99, 0.99));
    var t = report.byLevel().get(RiskLevel.HIGH).suggestedThreshold();
    DefaultReviewPolicy runtime = new DefaultReviewPolicy(Map.of(RiskLevel.HIGH, t));
    OcrFieldEvidence e =
        new OcrFieldEvidence(
            "field",
            "value",
            null,
            1,
            null,
            "eng",
            0.99,
            ConfidenceSource.OCR_ENGINE,
            ValidationStatus.PASSED,
            ReviewStatus.PENDING);
    var decision =
        runtime.decide(new FieldReviewContext(e, 0.99, RiskLevel.HIGH, CrossCheck.NOT_RUN));
    assertThat(decision).isEqualTo(ReviewDecision.AUTO_PASS);
  }

  @Test
  void config_snippet_uses_uppercase_enum_keys_and_all_four_fields() {
    List<CalibrationSample> samples = new ArrayList<>();
    samples.addAll(clean(10, RiskLevel.CRITICAL, 0.999, 0.99));
    samples.addAll(clean(10, RiskLevel.LOW, 0.90, 0.80));
    String snippet = new ThresholdCalibrator().calibrate(samples).toConfigSnippet();
    assertThat(snippet).contains("CRITICAL:", "LOW:", "auto-pass-confidence:", "manual-quality:");
  }

  @Test
  void empty_samples_and_invalid_target_are_rejected_cleanly() {
    CalibrationReport empty = new ThresholdCalibrator().calibrate(List.of());
    assertThat(empty.byLevel()).isEmpty();
    assertThat(empty.warnings()).isNotEmpty();
    assertThat(empty.allLevelsProven()).isFalse();
    assertThatThrownBy(() -> new ThresholdCalibrator(0.0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CalibrationSample.of(RiskLevel.HIGH, 1.5, 0.9, true))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
