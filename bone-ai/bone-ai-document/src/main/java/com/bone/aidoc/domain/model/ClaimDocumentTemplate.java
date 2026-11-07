package com.bone.aidoc.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 单一票种的版式契约（ADR-0046 待续项「具体票据版式解析模板」的领域形态）。
 *
 * <p>模板只声明两件与可信度直接相关的事实：<b>该票种必须出现哪些关键字段</b>（漏识别不得静默消失）与 <b>该票种优先使用哪些引擎</b>（分票种选型）。字段值如何抽取仍由 OCR
 * 引擎负责，模板不改写字段原值。
 *
 * <p>自由文本类票种（病历 / 出院小结 / 处方）字段名不稳定，<b>刻意不声明必填字段</b>：宁可少一道检查， 也不能因 schema 差异批量制造假红、最终诱使使用者关闭闸门。
 *
 * @param docType 票种
 * @param requiredFields 必填关键字段（空表示不启用缺失拦截）
 * @param preferredEngineIds 引擎优先顺序（空表示沿用配置顺序）
 */
public record ClaimDocumentTemplate(
    OcrDocType docType, List<RequiredField> requiredFields, List<String> preferredEngineIds) {

  public ClaimDocumentTemplate {
    requiredFields = requiredFields == null ? List.of() : List.copyOf(requiredFields);
    preferredEngineIds = preferredEngineIds == null ? List.of() : List.copyOf(preferredEngineIds);
  }

  /** 空契约：不拦截、不改变引擎顺序（GENERAL / 未登记票种的保守默认）。 */
  public static ClaimDocumentTemplate empty(OcrDocType docType) {
    return new ClaimDocumentTemplate(docType, List.of(), List.of());
  }

  /**
   * 字段名归一化：转小写 + 去除分隔符与各类空白（含全角空格），供别名精确比对。
   *
   * <p>例：{@code Total_Amount} / {@code total amount} / {@code totalAmount} 均归一为 {@code
   * totalamount}。
   */
  public static String normalize(String name) {
    if (name == null) {
      return "";
    }
    return name.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}_./\\\\-]", "");
  }

  /** 未出现在识别结果中的必填字段（入参为已归一化字段名集合）。 */
  public List<RequiredField> missingFields(Set<String> normalizedRecognizedNames) {
    List<RequiredField> missing = new ArrayList<>();
    for (RequiredField required : requiredFields) {
      boolean found = false;
      for (String recognized : normalizedRecognizedNames) {
        if (required.matches(recognized)) {
          found = true;
          break;
        }
      }
      if (!found) {
        missing.add(required);
      }
    }
    return missing;
  }

  /** 模板声明的字段风险；未声明时返回空，由全局启发式回退。 */
  public Optional<RiskLevel> riskOf(String normalizedFieldName) {
    for (RequiredField required : requiredFields) {
      if (required.matches(normalizedFieldName)) {
        return Optional.of(required.risk());
      }
    }
    return Optional.empty();
  }

  /** 是否启用了必填字段缺失拦截。 */
  public boolean enforcesRequiredFields() {
    return !requiredFields.isEmpty();
  }
}
