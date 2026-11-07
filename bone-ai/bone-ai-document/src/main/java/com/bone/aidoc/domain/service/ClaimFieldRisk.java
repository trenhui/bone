package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.RiskLevel;
import java.util.Set;

/**
 * 理赔字段风险分级（ADR-0046 §四：不同字段/票种应分别标定阈值）。
 *
 * <p><b>模板声明优先、全局启发式回退</b>：先查 {@link ClaimDocumentTemplateRegistry} 中该票种为该字段 声明的风险等级；未声明（或票种为
 * GENERAL / 自由文本类）时回退到按字段名的默认启发式。 分级结果喂给 {@code ReviewPolicy} 做差异化放行。
 *
 * <p>此为骨架启发式，真实分级须结合票种与保单责任，按真实标注数据标定。
 */
public class ClaimFieldRisk {

  private static final Set<String> CRITICAL_FIELDS =
      Set.of(
          "total_amount",
          "paid_amount",
          "claim_amount",
          "reimburse_amount",
          "invoice_amount",
          "amount",
          "sum_amount");

  private static final Set<String> HIGH_FIELDS =
      Set.of(
          "invoice_no",
          "id_card_no",
          "bank_card_no",
          "cert_no",
          "policy_no",
          "social_security_no",
          "date",
          "invoice_date",
          "start_date",
          "end_date",
          "visit_date",
          "period");

  private static final Set<String> MEDIUM_FIELDS =
      Set.of("hospital_name", "patient_name", "diagnosis", "address", "bank_name");

  private final ClaimDocumentTemplateRegistry templates;

  /** 默认：采用内置票种模板。 */
  public ClaimFieldRisk() {
    this(ClaimDocumentTemplateRegistry.builtIn());
  }

  /** 注入自定义（或空）模板注册表，便于标定与单测隔离。 */
  public ClaimFieldRisk(ClaimDocumentTemplateRegistry templates) {
    this.templates = templates == null ? ClaimDocumentTemplateRegistry.noTemplates() : templates;
  }

  /** 字段风险等级：模板声明优先，否则按字段名小写包含匹配（宽松适配不同 schema 命名）。 */
  public RiskLevel levelOf(OcrDocType docType, String fieldName) {
    return templates.riskOf(docType, fieldName).orElseGet(() -> globalLevel(fieldName));
  }

  private static RiskLevel globalLevel(String fieldName) {
    String f = fieldName == null ? "" : fieldName.toLowerCase();
    if (CRITICAL_FIELDS.stream().anyMatch(f::contains)) {
      return RiskLevel.CRITICAL;
    }
    if (HIGH_FIELDS.stream().anyMatch(f::contains)) {
      return RiskLevel.HIGH;
    }
    if (MEDIUM_FIELDS.stream().anyMatch(f::contains)) {
      return RiskLevel.MEDIUM;
    }
    return RiskLevel.LOW;
  }
}
