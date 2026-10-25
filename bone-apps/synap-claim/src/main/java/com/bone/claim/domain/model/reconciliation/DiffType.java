package com.bone.claim.domain.model.reconciliation;

/** 对账差异类型（§11.7 三向对账，字段真源 §9.5 reconciliation_diff.diff_type）。 */
public enum DiffType {
  /** 金额不一致（本方金额 ≠ 对方金额）。 */
  AMOUNT_MISMATCH,
  /** 状态不一致（本方资金阶段 ≠ 对方状态）。 */
  STATUS_MISMATCH,
  /** 本方缺失（对方有、本方无）。 */
  MISSING_LOCAL,
  /** 对方缺失（本方有、对方无）。 */
  MISSING_REMOTE,
  /** 额度守恒破坏（available ≠ initial − claimed − frozen）。 */
  QUOTA_BREAK
}
