package com.bone.claim.domain.model.evidence.valueobject;

/** 材料类型（delivery/05 数据字典枚举真源）。 */
public enum DocType {
  /** 发票。 */
  INVOICE,
  /** 费用清单。 */
  LIST,
  /** 病历。 */
  MEDICAL_RECORD,
  /** 医保结算单（三段拆分数据源，R-DED-03）。 */
  MEDICAL_SETTLEMENT
}
