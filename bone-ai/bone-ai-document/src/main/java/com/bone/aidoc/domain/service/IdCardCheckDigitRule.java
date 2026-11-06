package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.FieldValidationRule;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ValidationResult;
import java.util.Set;

/**
 * L2 规则：身份证校验位（GB 11643-1999 18 位，加权模 11）。
 *
 * <p>理赔核身强字段：前 17 位加权求和取模 11，对照校验码 {@code 10X98765432}。 识别错位（如把 X 识成 0、末尾数字串位）会直接判失败转人工，
 * 比单纯置信度更可靠、可解释、可审计。
 */
public class IdCardCheckDigitRule implements FieldValidationRule {

  private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
  private static final String CHECK_CODES = "10X98765432";

  @Override
  public String ruleId() {
    return "id-card-check-digit";
  }

  @Override
  public Set<OcrDocType> appliesTo() {
    return Set.of();
  }

  @Override
  public boolean appliesToField(OcrFieldEvidence field) {
    return field.fieldName() != null && field.fieldName().toLowerCase().contains("id_card");
  }

  @Override
  public ValidationResult validate(OcrFieldEvidence field, OcrDocType docType) {
    String v = field.value();
    if (v == null || v.isBlank()) {
      return ValidationResult.notApplicable();
    }
    String id = v.trim().toUpperCase(java.util.Locale.ROOT);
    if (id.length() != 18) {
      return ValidationResult.failed("身份证号长度应为 18 位: " + v);
    }
    int sum = 0;
    for (int i = 0; i < 17; i++) {
      char c = id.charAt(i);
      if (c < '0' || c > '9') {
        return ValidationResult.failed("身份证前 17 位须为数字: " + v);
      }
      sum += (c - '0') * WEIGHTS[i];
    }
    char expected = CHECK_CODES.charAt(sum % 11);
    char actual = id.charAt(17);
    if (expected != actual) {
      return ValidationResult.failed(
          String.format("身份证校验位不符（期望 %c，实际 %c）: %s", expected, actual, v));
    }
    return ValidationResult.passed();
  }
}
