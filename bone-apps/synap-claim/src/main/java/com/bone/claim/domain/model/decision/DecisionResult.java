package com.bone.claim.domain.model.decision;

/**
 * 决策图执行结论（落 {@code claim_decision_execution.decision_result}，值域与 05 契约 §2.2 同源）。
 *
 * <p><b>口径纪律</b>：{@code decisionResult} = 决策图结论（本枚举）；{@code decision} = 人工审核结论 （{@code
 * APPROVED}/{@code REJECTED}/{@code REQUEST_MORE_EVIDENCE}）。两者语义不同，字段名不得互换。
 */
public enum DecisionResult {
  /** 自动通过（跳过人工审核态，但仍须产出完整决策轨迹并纳入后督抽检）。 */
  AUTO_APPROVE,
  /** 系统建议拒赔（待人工确认后方生效，拒赔必填五要素）。 */
  SUGGEST_REJECT,
  /** 强制转人工（fail-safe：任何无法判定一律转人工，不得默认通过）。 */
  MANUAL_REQUIRED,
  /** 缺件（转补件流转）。 */
  NEED_MORE_EVIDENCE
}
