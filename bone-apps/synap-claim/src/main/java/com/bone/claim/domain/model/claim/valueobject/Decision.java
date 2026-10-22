package com.bone.claim.domain.model.claim.valueobject;

/** 人工审核决策（对外 {@code decision} 字段，delivery/05 唯一真源）。 */
public enum Decision {

  /** 审核通过。 */
  APPROVED,
  /** 拒赔（必填拒赔五要素）。 */
  REJECTED,
  /** 要求补件。 */
  REQUEST_MORE_EVIDENCE
}
