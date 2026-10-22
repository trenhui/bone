package com.bone.claim.domain.model.claim.valueobject;

/**
 * 决策结果（决策图执行结论，落 {@code claim_decision_trace.decision_result}）。
 *
 * <p><b>与人工审核决策（{@code decision} = APPROVED/REJECTED/REQUEST_MORE_EVIDENCE） 语义不同、字段名不得互换</b>（工程落地版
 * §6.3 字段名口径）。
 */
public enum DecisionResult {

  /** 自动通过（≤500 元 + 材料完整 + 无风险 + 责任明确，R-FAST-01）。 */
  AUTO_APPROVE,
  /** 系统建议拒赔（转人工确认后生效）。 */
  SUGGEST_REJECT,
  /** 强制转人工（≥1 万 / 风险命中 / AI<0.7 / OCR<0.7）。 */
  MANUAL_REQUIRED,
  /** 缺件。 */
  NEED_MORE_EVIDENCE
}
