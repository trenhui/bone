package com.bone.aidoc.domain.model;

/** OCR 识别文档类型（TPA 场景：发票 / 病历 / 处方 / 通用）。 */
public enum OcrDocType {
  /** 医疗发票 / 收费票据。 */
  INVOICE,
  /** 病历 / 诊断证明 / 出院小结。 */
  MEDICAL_RECORD,
  /** 处方单。 */
  PRESCRIPTION,
  /** 通用票据 / 其他。 */
  GENERAL
}
