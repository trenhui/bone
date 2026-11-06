package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ValidationStatus;
import org.junit.jupiter.api.Test;

/** {@link DateLegalityRule} 断言（日期合法性）。 */
class DateLegalityRuleTest {

  private final DateLegalityRule rule = new DateLegalityRule();

  private OcrFieldEvidence field(String value) {
    return new OcrFieldEvidence(
        "invoice_date",
        value,
        null,
        1,
        null,
        "stub",
        0.99,
        com.bone.aidoc.domain.model.ConfidenceSource.OCR_ENGINE,
        com.bone.aidoc.domain.model.ValidationStatus.PENDING,
        com.bone.aidoc.domain.model.ReviewStatus.PENDING);
  }

  @Test
  void dash_format_passes() {
    assertThat(rule.validate(field("2026-10-15"), OcrDocType.INVOICE).status())
        .isEqualTo(ValidationStatus.PASSED);
  }

  @Test
  void chinese_format_passes() {
    assertThat(rule.validate(field("2026年10月15日"), OcrDocType.INVOICE).status())
        .isEqualTo(ValidationStatus.PASSED);
  }

  @Test
  void invalid_month_fails() {
    assertThat(rule.validate(field("2026-13-40"), OcrDocType.INVOICE).status())
        .isEqualTo(ValidationStatus.FAILED);
  }

  @Test
  void garbage_fails() {
    assertThat(rule.validate(field("2026年月"), OcrDocType.INVOICE).status())
        .isEqualTo(ValidationStatus.FAILED);
  }
}
