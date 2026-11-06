package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.FieldValidationRule;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ValidationResult;
import java.util.Set;

/**
 * L2 规则：金额数值格式合法性（ADR-0046 §四 金额准确性）。
 *
 * <p>校验识别出的金额字段是否为<b>非负、最多两位小数</b>的合法数值（剔除货币符号与千分位）。 金额被模型「推理修正」或识别成乱码是理赔最致命的错误， 此规则作为第一道确定性闸门。与
 * {@link AmountCaseConsistencyRule} 互补： 本规则管「是不是合法数字」，后者管「数字与大写是否自洽」。
 */
public class NumericAmountRule implements FieldValidationRule {

  @Override
  public String ruleId() {
    return "amount-numeric-format";
  }

  @Override
  public Set<OcrDocType> appliesTo() {
    return Set.of();
  }

  @Override
  public boolean appliesToField(OcrFieldEvidence field) {
    return field.fieldName() != null && field.fieldName().toLowerCase().contains("amount");
  }

  @Override
  public ValidationResult validate(OcrFieldEvidence field, OcrDocType docType) {
    String v = field.value();
    if (v == null || v.isBlank()) {
      return ValidationResult.notApplicable();
    }
    String cleaned = v.replaceAll("[\\s¥￥$,]", "");
    if (cleaned.startsWith("-")) {
      return ValidationResult.failed("金额不应为负: " + v);
    }
    if (!cleaned.matches("\\d+(\\.\\d{1,2})?")) {
      return ValidationResult.failed("金额格式非法（应为非负、最多两位小数）: " + v);
    }
    return ValidationResult.passed();
  }
}
