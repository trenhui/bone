package com.bone.aidoc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 证据定位有效性：负坐标与越界页码不得计入「关键字段 100% 证据定位覆盖率」。 */
class BoundingBoxTest {

  @Test
  void negative_coordinates_are_invalid() {
    assertThat(new BoundingBox(-100, -100, -50, -50).valid()).isFalse();
    assertThat(new BoundingBox(-1, 0, 10, 10).valid()).isFalse();
    assertThat(new BoundingBox(0, 0, 10, 10).valid()).isTrue();
    assertThat(new BoundingBox(0, 0, 0, 10).valid()).isFalse();
  }

  @Test
  void page_beyond_page_count_is_not_valid_evidence() {
    OcrFieldEvidence e =
        new OcrFieldEvidence(
            "total_amount",
            "100.00",
            null,
            5,
            new BoundingBox(0, 0, 10, 10),
            "eng",
            0.99,
            ConfidenceSource.OCR_ENGINE,
            ValidationStatus.PASSED,
            ReviewStatus.AUTO_PASSED);
    assertThat(e.hasEvidenceLocation()).isTrue();
    assertThat(e.hasValidEvidenceLocation(3)).isFalse();
    assertThat(e.hasValidEvidenceLocation(5)).isTrue();
    // pageCount 未给出时只能校验下限，属已知边界
    assertThat(e.hasValidEvidenceLocation(0)).isTrue();
  }
}
