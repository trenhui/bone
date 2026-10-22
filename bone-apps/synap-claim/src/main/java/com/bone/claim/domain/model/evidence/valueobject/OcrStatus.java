package com.bone.claim.domain.model.evidence.valueobject;

/** 解析状态（delivery/05 ocr_status 枚举）。 */
public enum OcrStatus {
  /** 待解析。 */
  PENDING,
  /** 解析中。 */
  PARSING,
  /** 解析完成。 */
  DONE,
  /** 解析失败（转人工补录）。 */
  FAILED,
  /** 人工补录完成。 */
  MANUAL
}
