package com.bone.claim.domain.model.decision;

/** 决策节点执行状态（落 {@code claim_decision_trace.action_result} 语义位）。 */
public enum DecisionNodeStatus {
  /** 成功。 */
  SUCCEEDED,
  /** 失败（确定性节点失败 → 中止并转人工）。 */
  FAILED,
  /** 降级（LLM 不可用 → 降级纯规则路径）。 */
  FALLBACK,
  /** 跳过（无处理器/前置未满足）。 */
  SKIPPED
}
