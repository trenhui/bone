package com.bone.aidoc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OcrFieldEvidenceTest {

  @Test
  void from_propagates_page_and_bbox_from_field() {
    RecognizedField rf =
        new RecognizedField("total_amount", "100.00", 0.999, 3, new BoundingBox(1, 2, 30, 40));
    OcrFieldEvidence e = OcrFieldEvidence.from(rf, "paddle-ocr");

    assertThat(e.fieldName()).isEqualTo("total_amount");
    assertThat(e.value()).isEqualTo("100.00");
    assertThat(e.page()).isEqualTo(3);
    assertThat(e.bbox()).isNotNull();
    assertThat(e.hasEvidenceLocation()).isTrue();
    assertThat(e.engineId()).isEqualTo("paddle-ocr");
    assertThat(e.confidence()).isEqualTo(0.999);
    assertThat(e.confidenceSource()).isEqualTo(ConfidenceSource.OCR_ENGINE);
    assertThat(e.validationStatus()).isEqualTo(ValidationStatus.PENDING);
    assertThat(e.reviewStatus()).isEqualTo(ReviewStatus.PENDING);
  }

  @Test
  void from_with_null_page_falls_back_to_one() {
    RecognizedField rf = new RecognizedField("hospital_name", "某医院", 0.99, null, null);
    OcrFieldEvidence e = OcrFieldEvidence.from(rf, "eng");
    assertThat(e.page()).isEqualTo(1);
    assertThat(e.hasEvidenceLocation()).isFalse();
  }

  @Test
  @SuppressWarnings("deprecation")
  void deprecated_three_arg_overrides_page_and_keeps_bbox() {
    RecognizedField rf =
        new RecognizedField("invoice_no", "FP1", 0.98, 5, new BoundingBox(0, 0, 5, 5));
    OcrFieldEvidence e = OcrFieldEvidence.from(rf, "eng", 2);
    assertThat(e.page()).isEqualTo(2);
    assertThat(e.bbox()).isNotNull();
  }
}
