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
  MEDICAL_SETTLEMENT,
  /** 身份证/身份凭证。 */
  ID_CARD,
  /** 银行卡/账户凭证。 */
  BANK_CARD,
  /** 处方。 */
  PRESCRIPTION,
  /** 保单/保险单。 */
  POLICY,
  /** 出院小结/出院记录。 */
  DISCHARGE_SUMMARY,
  /** 签名/签字页（低墨迹高集中无表格的版式特征，可识别 hash 文件名影像）。 */
  SIGNATURE,
  /** 其他（自动分类未命中时的 fail-closed 归类，不冒充发票）。 */
  OTHER
}
