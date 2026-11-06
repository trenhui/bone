package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.RiskLevel;
import java.util.Set;

/**
 * 理赔字段风险分级（ADR-0046 §四：不同字段应分别标定阈值）。
 *
 * <p>直接涉钱字段（金额/赔付额）为 CRITICAL；证件号、发票代码、日期为 HIGH； 机构名/诊断/地址为 MEDIUM；其余为 LOW。分级结果喂给 {@code
 * ReviewPolicy} 做差异化放行。 此为默认启发式，真实分级须结合票种与保单责任，按真实标注数据标定。
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

  /** 字段风险等级（按字段名小写包含匹配，宽松适配不同 schema 命名）。 */
  public RiskLevel levelOf(OcrDocType docType, String fieldName) {
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
