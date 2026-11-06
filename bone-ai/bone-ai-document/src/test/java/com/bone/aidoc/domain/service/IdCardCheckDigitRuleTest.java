package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ValidationStatus;
import org.junit.jupiter.api.Test;

/** {@link IdCardCheckDigitRule} 断言（GB 11643-1999 校验位）。 */
class IdCardCheckDigitRuleTest {

  private final IdCardCheckDigitRule rule = new IdCardCheckDigitRule();
  private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
  private static final String CHECK = "10X98765432";

  private OcrFieldEvidence field(String value) {
    return new OcrFieldEvidence(
        "id_card_no",
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

  private String validId(String prefix17) {
    int sum = 0;
    for (int i = 0; i < 17; i++) {
      sum += (prefix17.charAt(i) - '0') * WEIGHTS[i];
    }
    return prefix17 + CHECK.charAt(sum % 11);
  }

  @Test
  void valid_id_passes() {
    String id = validId("11010519491231002");
    assertThat(rule.validate(field(id), OcrDocType.ID_CARD).status())
        .isEqualTo(ValidationStatus.PASSED);
  }

  @Test
  void tampered_check_digit_fails() {
    String id = validId("11010519491231002");
    char wrong = id.charAt(17) == '0' ? '1' : '0';
    assertThat(rule.validate(field(id.substring(0, 17) + wrong), OcrDocType.ID_CARD).status())
        .isEqualTo(ValidationStatus.FAILED);
  }

  @Test
  void wrong_length_fails() {
    assertThat(rule.validate(field("11010519491231"), OcrDocType.ID_CARD).status())
        .isEqualTo(ValidationStatus.FAILED);
  }
}
