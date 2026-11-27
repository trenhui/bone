/**
 * 理赔材料类型展示口径（与后端 DocType 枚举一一对应）。
 *
 * 原先在 EvidenceUploadPage 与 EvidenceGallery 各硬编码一份，新增类型时必然漏改一处；
 * 统一从本文件导出，分类树、上传下拉与工作台标签共用。
 */
export const DOC_TYPES: { value: string; label: string }[] = [
  { value: 'INVOICE', label: '发票' },
  { value: 'LIST', label: '费用清单' },
  { value: 'MEDICAL_RECORD', label: '病历' },
  { value: 'MEDICAL_SETTLEMENT', label: '医保结算单' },
  { value: 'ID_CARD', label: '身份证' },
  { value: 'BANK_CARD', label: '银行卡' },
  { value: 'PRESCRIPTION', label: '处方' },
  { value: 'POLICY', label: '保单' },
  { value: 'DISCHARGE_SUMMARY', label: '出院小结' },
  { value: 'SIGNATURE', label: '签名' },
  { value: 'OTHER', label: '其他' },
];

/** 上传下拉专属：AUTO 是前端伪类型，不发给后端 DocType，而是转成 autoClassify=true。 */
export const DOC_TYPE_UPLOAD_OPTIONS: { value: string; label: string }[] = [
  { value: 'AUTO', label: '自动识别（按影像内容）' },
  ...DOC_TYPES,
];

export const docTypeLabel = (value?: string): string =>
  DOC_TYPES.find((item) => item.value === value)?.label ?? value ?? '-';
