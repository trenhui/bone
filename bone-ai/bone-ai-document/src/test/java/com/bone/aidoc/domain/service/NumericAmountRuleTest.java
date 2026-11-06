package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ValidationResult;
import org.junit.jupiter.api.Test;

/** {@link NumericAmountRule} 断言（金额数值格式合法性）。 */
class NumericAmountRuleTest {

  private final NumericAmountRule rule = new NumericAmountRule();

  private OcrFieldEvidence field(String name, String value) {
    return new OcrFieldEvidence(
        name,
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
  void valid_amount_passes() {
    assertThat(rule.validate(field("total_amount", "12580.00"), OcrDocType.INVOICE).status())
        .isEqualTo(com.bone.aidoc.domain.model.ValidationStatus.PASSED);
  }

  @Test
  void amount_with_currency_and_thousands_passes() {
    assertThat(rule.validate(field("amount", "￥12,580.50"), OcrDocType.INVOICE).status())
        .isEqualTo(com.bone.aidoc.domain.model.ValidationStatus.PASSED);
  }

  @Test
  void negative_amount_fails() {
    ValidationResult r = rule.validate(field("total_amount", "-100.00"), OcrDocType.INVOICE);
    assertThat(r.status()).isEqualTo(com.bone.aidoc.domain.model.ValidationStatus.FAILED);
    assertThat(r.message()).contains("不应为负");
  }

  @Test
  void non_numeric_fails() {
    assertThat(rule.validate(field("total_amount", "壹万"), OcrDocType.INVOICE).status())
        .isEqualTo(com.bone.aidoc.domain.model.ValidationStatus.FAILED);
  }

  @Test
  void too_many_decimals_fails() {
    assertThat(rule.validate(field("total_amount", "1.234"), OcrDocType.INVOICE).status())
        .isEqualTo(com.bone.aidoc.domain.model.ValidationStatus.FAILED);
  }

  @Test
  void blank_value_not_applicable() {
    assertThat(rule.validate(field("total_amount", ""), OcrDocType.INVOICE).status())
        .isEqualTo(com.bone.aidoc.domain.model.ValidationStatus.NOT_APPLICABLE);
  }
}
