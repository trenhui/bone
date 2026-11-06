package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ValidationStatus;
import org.junit.jupiter.api.Test;

/** {@link AmountCaseConsistencyRule} 断言（金额大小写一致性）。 */
class AmountCaseConsistencyRuleTest {

  private final AmountCaseConsistencyRule rule = new AmountCaseConsistencyRule();

  private OcrFieldEvidence field(String value, String rawText) {
    return new OcrFieldEvidence(
        "total_amount",
        value,
        rawText,
        1,
        null,
        "stub",
        0.99,
        com.bone.aidoc.domain.model.ConfidenceSource.OCR_ENGINE,
        com.bone.aidoc.domain.model.ValidationStatus.PENDING,
        com.bone.aidoc.domain.model.ReviewStatus.PENDING);
  }

  @Test
  void consistent_passes() {
    assertThat(rule.validate(field("12580.00", "合计金额：壹万贰仟伍佰捌拾元整"), OcrDocType.INVOICE).status())
        .isEqualTo(ValidationStatus.PASSED);
  }

  @Test
  void inconsistent_fails() {
    ValidationStatus s =
        rule.validate(field("12580.00", "壹万贰仟伍佰捌拾壹元整"), OcrDocType.INVOICE).status();
    assertThat(s).isEqualTo(ValidationStatus.FAILED);
  }

  @Test
  void missing_chinese_not_applicable() {
    assertThat(rule.validate(field("12580.00", "合计金额：xxx"), OcrDocType.INVOICE).status())
        .isEqualTo(ValidationStatus.NOT_APPLICABLE);
  }
}
