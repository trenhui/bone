package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.FieldValidationRule;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ValidationResult;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * L2 规则：日期合法性（ADR-0046 §四 日期准确性）。
 *
 * <p>校验识别出的日期字段能否解析为合法日历日，覆盖理赔常见写法： {@code yyyy-MM-dd} / {@code yyyy.MM.dd} / {@code yyyy/MM/dd} /
 * {@code yyyyMMdd} / {@code yyyy年MM月dd日}。 无效（如 2026-13-40、串列到错误年份）直接判失败，交由人工复核。
 */
public class DateLegalityRule implements FieldValidationRule {

  private static final List<DateTimeFormatter> FORMATTERS =
      List.of(
          DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT),
          DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT),
          DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT),
          DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT),
          DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.ROOT));

  @Override
  public String ruleId() {
    return "date-legality";
  }

  @Override
  public Set<OcrDocType> appliesTo() {
    return Set.of();
  }

  @Override
  public boolean appliesToField(OcrFieldEvidence field) {
    return field.fieldName() != null && field.fieldName().toLowerCase().contains("date");
  }

  @Override
  public ValidationResult validate(OcrFieldEvidence field, OcrDocType docType) {
    String v = field.value();
    if (v == null || v.isBlank()) {
      return ValidationResult.notApplicable();
    }
    String cleaned = v.trim().replaceAll("\\s+", "");
    for (DateTimeFormatter f : FORMATTERS) {
      try {
        LocalDate.parse(cleaned, f);
        return ValidationResult.passed();
      } catch (DateTimeParseException ignored) {
        // 尝试下一个格式
      }
    }
    return ValidationResult.failed("日期无法解析为合法日历: " + v);
  }
}
