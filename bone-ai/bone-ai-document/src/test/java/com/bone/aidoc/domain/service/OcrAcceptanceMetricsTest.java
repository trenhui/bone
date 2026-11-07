package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.BoundingBox;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.QualityReport;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.TrustedOcrDocument;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 验收指标：证据链为准、关键字段覆盖率、聚合计数不一致时的反查能力。 */
class OcrAcceptanceMetricsTest {

  private static OcrFieldEvidence evidence(String name, boolean located, ReviewStatus status) {
    return new OcrFieldEvidence(
        name,
        "v",
        null,
        1,
        located ? new BoundingBox(0, 0, 10, 10) : null,
        "eng",
        0.99,
        com.bone.aidoc.domain.model.ConfidenceSource.OCR_ENGINE,
        com.bone.aidoc.domain.model.ValidationStatus.PASSED,
        status);
  }

  private static TrustedOcrDocument doc(
      List<OcrFieldEvidence> fields, Map<ReviewStatus, Integer> counts) {
    return new TrustedOcrDocument(
        "d1",
        "t1",
        "ref",
        OcrDocType.INVOICE,
        "eng",
        List.of("eng"),
        fields,
        QualityReport.none("none"),
        "raw",
        1,
        counts);
  }

  @Test
  void computes_rates_from_evidence_not_from_aggregate_counts() {
    List<OcrFieldEvidence> fields =
        List.of(
            evidence("total_amount", true, ReviewStatus.AUTO_PASSED),
            evidence("hospital_name", false, ReviewStatus.MANUAL_REVIEW));
    // 故意给出与证据不一致的聚合计数
    TrustedOcrDocument d = doc(fields, Map.of(ReviewStatus.AUTO_PASSED, 99));

    OcrAcceptanceMetrics m = OcrAcceptanceMetrics.from(d, Set.of("total_amount"));
    assertThat(m.totalFields()).isEqualTo(2);
    assertThat(m.autoPassed()).isEqualTo(1);
    assertThat(m.manualReview()).isEqualTo(1);
    assertThat(m.autoPassRate()).isEqualTo(0.5);
    assertThat(d.autoPassRate()).isEqualTo(49.5);
    assertThat(m.criticalFields()).isEqualTo(1);
    assertThat(m.criticalFieldsWithEvidence()).isEqualTo(1);
    assertThat(m.criticalEvidenceCoverage()).isEqualTo(1.0);
    assertThat(m.meetsKeyFieldEvidenceCoverageTarget()).isTrue();
  }

  @Test
  void missing_required_fields_are_counted_and_lower_coverage() {
    OcrFieldEvidence missing = OcrFieldEvidence.missing("total_amount", "eng", OcrDocType.INVOICE);
    TrustedOcrDocument d =
        doc(List.of(evidence("hospital_name", true, ReviewStatus.AUTO_PASSED), missing), Map.of());

    OcrAcceptanceMetrics m = OcrAcceptanceMetrics.from(d, new ClaimFieldRisk());
    assertThat(m.missingRequiredFields()).isEqualTo(1);
    assertThat(m.manualReview()).isEqualTo(1);
    assertThat(m.manualInterventionRate()).isEqualTo(0.5);
    assertThat(m.criticalFields()).isEqualTo(1);
    assertThat(m.criticalFieldsWithEvidence()).isZero();
    assertThat(m.meetsKeyFieldEvidenceCoverageTarget()).isFalse();
  }

  @Test
  void critical_auto_passed_is_exposed_as_safety_probe() {
    TrustedOcrDocument d =
        doc(
            List.of(
                evidence("total_amount", false, ReviewStatus.AUTO_PASSED),
                evidence("invoice_no", true, ReviewStatus.SECONDARY_RECOGNITION)),
            Map.of());
    OcrAcceptanceMetrics m = OcrAcceptanceMetrics.from(d, new ClaimFieldRisk());
    assertThat(m.criticalAutoPassed()).isEqualTo(1);
    assertThat(m.secondaryRecognition()).isEqualTo(1);
    assertThat(m.criticalEvidenceCoverage()).isZero();
  }

  @Test
  void empty_document_never_fakes_green() {
    TrustedOcrDocument d = doc(List.of(), Map.of());
    OcrAcceptanceMetrics m = OcrAcceptanceMetrics.from(d, new ClaimFieldRisk());
    assertThat(m.totalFields()).isZero();
    assertThat(m.autoPassRate()).isZero();
    assertThat(m.criticalEvidenceCoverage()).isZero();
    assertThat(m.meetsKeyFieldEvidenceCoverageTarget()).isFalse();
  }
}
