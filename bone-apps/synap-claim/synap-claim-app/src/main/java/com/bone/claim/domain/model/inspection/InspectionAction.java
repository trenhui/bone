package com.bone.claim.domain.model.inspection;

/**
 * 差异处置动作（§9.5 {@code action_taken}）。
 *
 * <p><b>一期边界</b>：本枚举只登记「打算怎么处置」，实际状态回流（§6.1 质检态 30/31/32 与重算/退款编排）属 V2；因此 {@code target_status}
 * 只做受控登记，不由本模块自动推进案件状态——避免「抽检一结论就自动改终态案件」这种无签核的资金动作。
 */
public enum InspectionAction {
  /** 触发重算（理算差异）。 */
  RECALCULATE,
  /** 转人工复核（AI 误判/规则漏命中）。 */
  REVIEW,
  /** 退款/追回（已支付且多付）。 */
  REFUND,
  /** 关闭/结案维持（差异不影响赔付结果）。 */
  CLOSE,
  /** 无处置（{@link InspectionResult#PASS} 唯一合法取值）。 */
  NONE
}
