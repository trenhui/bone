package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.BoundingBox;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.QualityReport;
import com.bone.aidoc.domain.model.RecognizedField;
import com.bone.aidoc.domain.model.ValidationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeuristicOcrQualityEvaluatorTest {

  private final HeuristicOcrQualityEvaluator evaluator = new HeuristicOcrQualityEvaluator();

  private OcrFieldEvidence field(
      String name, String value, double conf, boolean located, ValidationStatus vs) {
    RecognizedField rf =
        new RecognizedField(name, value, conf, 1, located ? new BoundingBox(0, 0, 10, 10) : null);
    return OcrFieldEvidence.from(rf, "eng").withValidationStatus(vs);
  }

  @Test
  void high_conf_passed_located_scores_high() {
    QualityReport r =
        evaluator.evaluate(
            List.of(field("total_amount", "100.00", 0.999, true, ValidationStatus.PASSED)),
            OcrDocType.INVOICE,
            "s");
    assertThat(r.evaluatorId()).isEqualTo("heuristic");
    assertThat(r.scoreFor("total_amount")).isGreaterThan(0.95);
  }

  @Test
  void missing_bbox_scores_lower_than_located() {
    double located =
        evaluator
            .evaluate(
                List.of(field("total_amount", "100.00", 0.999, true, ValidationStatus.PASSED)),
                OcrDocType.INVOICE,
                "s")
            .scoreFor("total_amount");
    double noBbox =
        evaluator
            .evaluate(
                List.of(field("total_amount", "100.00", 0.999, false, ValidationStatus.PASSED)),
                OcrDocType.INVOICE,
                "s")
            .scoreFor("total_amount");
    assertThat(noBbox).isLessThan(located);
  }

  @Test
  void low_conf_outlier_scores_lower_than_high() {
    QualityReport r =
        evaluator.evaluate(
            List.of(
                field("a", "x", 0.60, true, ValidationStatus.PASSED),
                field("b", "y", 0.99, true, ValidationStatus.PASSED)),
            OcrDocType.INVOICE,
            "s");
    assertThat(r.scoreFor("b")).isGreaterThan(r.scoreFor("a"));
  }

  @Test
  void blank_value_is_penalized() {
    double withValue =
        evaluator
            .evaluate(
                List.of(field("total_amount", "100.00", 0.999, true, ValidationStatus.PASSED)),
                OcrDocType.INVOICE,
                "s")
            .scoreFor("total_amount");
    double blank =
        evaluator
            .evaluate(
                List.of(field("total_amount", "", 0.999, true, ValidationStatus.PASSED)),
                OcrDocType.INVOICE,
                "s")
            .scoreFor("total_amount");
    assertThat(blank).isLessThan(withValue);
    assertThat(blank).isGreaterThanOrEqualTo(0.1);
  }

  @Test
  void failed_validation_scores_lower_than_passed() {
    double passed =
        evaluator
            .evaluate(
                List.of(field("total_amount", "100.00", 0.999, true, ValidationStatus.PASSED)),
                OcrDocType.INVOICE,
                "s")
            .scoreFor("total_amount");
    double failed =
        evaluator
            .evaluate(
                List.of(field("total_amount", "100.00", 0.999, true, ValidationStatus.FAILED)),
                OcrDocType.INVOICE,
                "s")
            .scoreFor("total_amount");
    assertThat(failed).isLessThan(passed);
  }

  @Test
  void empty_fields_yields_nan_overall() {
    QualityReport r = evaluator.evaluate(List.of(), OcrDocType.INVOICE, "s");
    assertThat(Double.isNaN(r.overall())).isTrue();
    assertThat(Double.isNaN(r.scoreFor("missing"))).isTrue();
  }

  @Test
  void score_for_unknown_field_is_nan() {
    QualityReport r =
        evaluator.evaluate(
            List.of(field("total_amount", "100.00", 0.999, true, ValidationStatus.PASSED)),
            OcrDocType.INVOICE,
            "s");
    assertThat(Double.isNaN(r.scoreFor("not_present"))).isTrue();
  }
}
