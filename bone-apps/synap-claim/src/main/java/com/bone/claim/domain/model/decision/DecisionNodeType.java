package com.bone.claim.domain.model.decision;

/**
 * 决策节点类型（对外 4 类，05 契约 §4.4 同源；理赔域不新增自定义类型）。
 *
 * <p>内部 5 节点 → 对外 4 类映射：{@code EvidenceLoad}→{@code OCR}、{@code RuleCheck}→{@code RULE}、 {@code
 * Adjust}→{@code RULE}、{@code AiAssess}→{@code LLM}、{@code Arbitrate}→{@code DECISION}。
 * 支付属资金域（{@code settle_funding_event}），不入决策 trace。
 */
public enum DecisionNodeType {
  OCR,
  RULE,
  LLM,
  DECISION
}
