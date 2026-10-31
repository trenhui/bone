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
  QUOTA_BREAK,
  /**
   * 额度投影漂移（{@code quota_account} 的计数与 {@code t_quota_transaction} 汇总不等）。
   *
   * <p>与 {@link #QUOTA_BREAK} 分开：守恒式只能证明"三值自洽"，证明不了"三值与流水一致"—— 一次错额的越权释放可以既破坏守恒、也可能让守恒式仍然成立（例如
   * claimed 与 frozen 一增一减）。 两者成因与处置不同：守恒破坏＝账目本身不平，立即冻结自动放款；投影漂移＝账户行是旧值， 按流水重算即可自愈（{@code
   * QuotaProjection#rebuild} + {@code QuotaAccount#reconcileProjection}）。
   */
  PROJECTION_DRIFT
}
