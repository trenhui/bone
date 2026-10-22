package com.bone.claim.common;

/**
 * synap-claim 业务错误码常量（{@code SC_} 前缀，稳定业务码 = i18n 键）。
 *
 * <p>每个常量必须在 {@link ClaimErrors#DEFAULT_HTTP_STATUS} 登记 HTTP 状态（类加载 fail-fast）。
 */
public final class ClaimErrorCodes {

  private ClaimErrorCodes() {}

  // ===== A1 赔案 =====

  /** 案件不存在或不可见（租户隔离下的统一 404 语义，防枚举）。 */
  public static final String CLAIM_NOT_FOUND = "SC_CLAIM_NOT_FOUND";

  /** 状态冲突（当前状态不允许该动作，§6.2 合法转换表）。 */
  public static final String CLAIM_STATUS_CONFLICT = "SC_CLAIM_STATUS_CONFLICT";

  /** 乐观锁冲突（版本竞争重试 ≤3 次后仍失败）。 */
  public static final String CLAIM_CONCURRENT_CONFLICT = "SC_CLAIM_CONCURRENT_CONFLICT";

  /** 保单不存在/未生效/被保人不匹配（报案前置校验）。 */
  public static final String CLAIM_POLICY_NOT_ACTIVE = "SC_CLAIM_POLICY_NOT_ACTIVE";

  /** 出险时间不在保单有效期内。 */
  public static final String CLAIM_OUT_OF_POLICY_PERIOD = "SC_CLAIM_OUT_OF_POLICY_PERIOD";

  /** 被保人不匹配（假名化 ID 比对失败）。 */
  public static final String CLAIM_INSURED_MISMATCH = "SC_CLAIM_INSURED_MISMATCH";

  /** 拒赔五要素缺失（REJECTED 必填）。 */
  public static final String CLAIM_REJECT_REASON_REQUIRED = "SC_CLAIM_REJECT_REASON_REQUIRED";

  /** 双人复核人必须为非第一审核人（职责分离）。 */
  public static final String CLAIM_REVIEWER_CONFLICT = "SC_CLAIM_REVIEWER_CONFLICT";

  // ===== A2 证据 =====

  /** 材料不存在或不可见。 */
  public static final String CLAIM_EVIDENCE_NOT_FOUND = "SC_CLAIM_EVIDENCE_NOT_FOUND";

  /** 重复材料（同案件同内容哈希，§10.5 409）。 */
  public static final String CLAIM_EVIDENCE_DUPLICATED = "SC_CLAIM_EVIDENCE_DUPLICATED";

  /** 材料份数超限（单案件 ≤20 份，§7.2）。 */
  public static final String CLAIM_EVIDENCE_LIMIT_EXCEEDED = "SC_CLAIM_EVIDENCE_LIMIT_EXCEEDED";

  /** 证据已锁定（锁定后禁止回填/补录，§4.3 不变量）。 */
  public static final String CLAIM_EVIDENCE_LOCKED = "SC_CLAIM_EVIDENCE_LOCKED";

  // ===== A4/A6 责任与理算 =====

  /** 未找到有效责任配置（计划未配置或已失效，理算无法进行）。 */
  public static final String CLAIM_LIABILITY_NOT_FOUND = "SC_CLAIM_LIABILITY_NOT_FOUND";

  /** 理算记录不存在。 */
  public static final String CLAIM_ADJUST_NOT_FOUND = "SC_CLAIM_ADJUST_NOT_FOUND";

  /** 结算单不存在。 */
  public static final String CLAIM_SETTLEMENT_NOT_FOUND = "SC_CLAIM_SETTLEMENT_NOT_FOUND";

  /** 结算状态冲突（已终态不可再操作，§6.4 阶段单调）。 */
  public static final String CLAIM_SETTLEMENT_CONFLICT = "SC_CLAIM_SETTLEMENT_CONFLICT";

  // ===== A3 决策执行 =====

  /** 同案已有活跃决策执行（每案仅一个活跃执行，§4.3 不变量）。 */
  public static final String CLAIM_DECISION_ACTIVE_CONFLICT = "SC_CLAIM_DECISION_ACTIVE_CONFLICT";

  // ===== A8 申诉 =====

  /** 申诉记录不存在（或已复议终态）。 */
  public static final String CLAIM_APPEAL_NOT_FOUND = "SC_CLAIM_APPEAL_NOT_FOUND";

  /** 证据未锁定即发起理算（理算前置不满足，§4.3）。 */
  public static final String CLAIM_ADJUST_EVIDENCE_NOT_LOCKED =
      "SC_CLAIM_ADJUST_EVIDENCE_NOT_LOCKED";

  // ===== A5 额度 =====

  /** 额度账户不存在。 */
  public static final String CLAIM_QUOTA_NOT_FOUND = "SC_CLAIM_QUOTA_NOT_FOUND";

  /** 可用额度不足（占额被拒，R-CAP 截断）。 */
  public static final String CLAIM_QUOTA_INSUFFICIENT = "SC_CLAIM_QUOTA_INSUFFICIENT";

  /** 额度状态冲突（已确认 CONFIRMED 禁止清除/重算，§5.3 硬不变量）。 */
  public static final String CLAIM_QUOTA_STATE_CONFLICT = "SC_CLAIM_QUOTA_STATE_CONFLICT";
}
