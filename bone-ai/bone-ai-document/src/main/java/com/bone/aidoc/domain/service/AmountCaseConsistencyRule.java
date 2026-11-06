package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.FieldValidationRule;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ValidationResult;
import java.util.Set;

/**
 * L2 规则：金额大小写一致性（ADR-0046 §四 金额准确性 + 文章反面案例：少负号 / 数字串列）。
 *
 * <p>当 OCR 同时给出<b>数字金额</b>（{@code value}）与<b>中文大写金额</b>（{@code rawText} 中含）时， 将两者解析为元并比对（误差 ≤ 0.005
 * 元即视为一致）。 不一致（如数字被模型「改对」、大小写错位、年份串列）直接判失败转人工。 任一侧无法解析时判「不适用」，交由 {@link NumericAmountRule}
 * 等其它规则处理，不误报。
 */
public class AmountCaseConsistencyRule implements FieldValidationRule {

  private static final double TOLERANCE = 0.005;

  @Override
  public String ruleId() {
    return "amount-case-consistency";
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
    double numeric = parseNumeric(field.value());
    if (Double.isNaN(numeric)) {
      return ValidationResult.notApplicable();
    }
    String chinese = ChineseAmount.extract(field.rawText());
    if (chinese == null) {
      return ValidationResult.notApplicable();
    }
    double yuan = ChineseAmount.parseYuan(chinese);
    if (Double.isNaN(yuan)) {
      return ValidationResult.notApplicable();
    }
    if (Math.abs(numeric - yuan) <= TOLERANCE) {
      return ValidationResult.passed();
    }
    return ValidationResult.failed(
        String.format("金额大小写不一致（数字 %s vs 大写 %.2f 元）: %s", field.value(), yuan, field.rawText()));
  }

  private static double parseNumeric(String value) {
    if (value == null || value.isBlank()) {
      return Double.NaN;
    }
    String cleaned = value.replaceAll("[\\s¥￥$,]", "");
    if (!cleaned.matches("\\d+(\\.\\d{1,2})?")) {
      return Double.NaN;
    }
    return Double.parseDouble(cleaned);
  }
}
