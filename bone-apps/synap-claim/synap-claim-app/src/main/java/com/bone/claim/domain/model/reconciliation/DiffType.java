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
  PROJECTION_DRIFT,
  /**
   * 资金阶段链不单调（§11.7 不变量 3）：流水里出现状态机不允许的迁移，或事件时间倒序。
   *
   * <p>与 {@link #STATUS_MISMATCH} 分开：后者只比「末条流水 = 结算单当前阶段」，对<b>逆向序列</b>无感—— 末条若是新追加的
   * SOFT，与结算单同步改过之后仍然相等。判定表见 {@code FundingStageChain} （合法迁移表就是 {@code FundingStage}
   * 的语义，不在对账服务里另写一份）。
   */
  STAGE_REGRESSION,
  /**
   * 案件与结算金额不一致（§11.7 不变量 6）：净已付 ≠ 该案最新理算总额。
   *
   * <p><b>为什么不比 {@code claim_case.approved_amount}</b>：那列是核赔决策时的快照，而结算金额取的是理算结果（资金权威口径，见 {@code
   * AutoApprovalApplicationService} 的金额口径说明）。核减过的案件两者天然不等， 按 {@code approved_amount}
   * 判会把正常案件全判成差异——判决式写错比没有判决式更糟。
   */
  CASE_SETTLE_MISMATCH,
  /**
   * 额度流水的余额链断裂（§11.7 不变量 1 ④）：某笔流水的 {@code before_amount} 接不上上一条同列流水的 {@code after_amount}， 或
   * {@code after_amount − before_amount} 与本笔金额（含方向）不等。
   *
   * <p>与 {@link #PROJECTION_DRIFT} 分开：后者判"账户现值 ≠ Σ流水"（结果是错的），本类型判"流水之间接不上"（过程是错的）。
   * 一笔并发丢失更新可以只让链断、而投影照样等于 Σ流水（因为投影本来就是按流水算的）——那时 {@code PROJECTION_DRIFT} 恒绿，
   * 只有串链看得见"两笔操作从同一个旧值出发"。判定表见 {@code QuotaLedgerChain}。
   */
  LEDGER_CHAIN_BREAK,
  /**
   * 大额双人复核留痕缺失（§11.7 不变量 5）：{@code review_first_approver} 为空、流水里没有 {@code REVIEW_FREEZE}、
   * 放行那条没留操作人，或复核人与审核发起人同一身份。
   *
   * <p>判定见 {@code ReviewEvidence}。它是<b>回溯取证</b>，与聚合在写路径上的前置拦截不是同一件事：后者防未来，前者证明过去（补数/迁移/直接改库绕不过它）。
   */
  REVIEW_EVIDENCE_MISSING
}
