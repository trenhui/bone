package com.bone.aiorch.domain.hitl;

/**
 * 人工裁决原因（唯一回流入口，自由文本不进管道——AI 中台 §4 治理原则）。
 *
 * <p>业界对位：AI Agent 裁决回流的受控字典——用枚举约束"为什么人工改判"，供规则/评测回流与影子比对，杜绝任意自由文本污染证据链。
 *
 * <ul>
 *   <li>{@link #RULE_MISS} 规则漏判（应命中而未命中）
 *   <li>{@link #CONTEXT_MISS} 上下文缺失（判定所需字段未到位）
 *   <li>{@link #SUGGESTION_WRONG} 建议本身错误
 *   <li>{@link #POLICY_CONFLICT} 与既有策略冲突
 * </ul>
 *
 * <p>{@code reject}/{@code modify} 必带 {@code reason_type}；{@code confirm} 可带（采纳时回流可选）。
 */
public enum ReasonType {
  RULE_MISS,
  CONTEXT_MISS,
  SUGGESTION_WRONG,
  POLICY_CONFLICT
}
