package com.bone.aidoc.domain.model;

/**
 * OCR 识别文档类型（智能理赔票种选型表，ADR-0046 §二）。
 *
 * <p>按票种分别选型是可信 OCR 的核心前提：身份证 / 银行卡 / 医疗发票 / 费用清单 / 病历 / 出院记录 / 保单的版式、识别难点与准确性要求差异很大，禁止用单一引擎处理所有材料。
 */
public enum OcrDocType {
  /** 医疗发票 / 收费票据。 */
  INVOICE,
  /** 病历 / 诊断证明。 */
  MEDICAL_RECORD,
  /** 处方单。 */
  PRESCRIPTION,
  /** 身份证。 */
  ID_CARD,
  /** 银行卡。 */
  BANK_CARD,
  /** 医疗费用清单（表格为主）。 */
  FEE_LIST,
  /** 出院记录 / 出院小结。 */
  DISCHARGE_SUMMARY,
  /** 保险单 / 保险责任材料。 */
  POLICY,
  /** 通用票据 / 其他。 */
  GENERAL
}
