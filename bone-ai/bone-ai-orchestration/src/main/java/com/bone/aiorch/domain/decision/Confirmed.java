package com.bone.aiorch.domain.decision;

import java.time.Instant;

/**
 * 已确认结论（{@code Confirmed}）：HITL 人工裁决或规则产出的**终态**载体——业务状态机唯一允许消费的决策形态。
 *
 * <p><b>与 {@link AiSuggestion} 的关系</b>：AI 建议进 {@link DecisionGate} 闸门裁决——自动放行线（置信度 / 金额 / AI
 * 可用性合取）或人工终审通过后，才升格为 {@code Confirmed}。两者是不同类型、无隐式转换：Java 没有 record 间的 隐式转换，ArchUnit 规则 6 再禁掉
 * {@code AiSuggestion} 越出决策包——「拿 AI 建议直接当业务决定」在编译期与架构门禁 两处被机械拦截（详设 §3.3 规则 6）。
 *
 * <p><b>不变量（确认必须是确认）</b>：{@code decidedBy}（谁确认）与 {@code evidenceRef}（凭什么确认）必填—— 缺任何一项的「确认」在审计上不可信，与
 * HITL {@code hitl_disposition.decided_by} 留痕同口径。
 *
 * @param value 确认值（confirm 采纳的建议值或 modify 的改判值）
 * @param decidedBy 裁决人（人工 actor / 规则码 / 系统 actor，如 SLA 升级）
 * @param evidenceRef 证据引用（traceId 等，回放关联键）
 * @param at 确认时刻（权威时钟由调用方注入，便于单测穷举）
 */
public record Confirmed<T>(T value, String decidedBy, String evidenceRef, Instant at) {

  public Confirmed {
    if (decidedBy == null || decidedBy.isBlank()) {
      throw new IllegalArgumentException("确认必须记录裁决人（decidedBy）：无裁决人的确认不可审计");
    }
    if (evidenceRef == null || evidenceRef.isBlank()) {
      throw new IllegalArgumentException("确认必须携带证据引用（evidenceRef）：无出处的确认不可回放");
    }
    if (at == null) {
      throw new IllegalArgumentException("确认时刻（at）必填：权威时钟由调用方注入");
    }
  }

  /** 便捷工厂：以当前时刻确认（生产路径；单测请显式传 {@code at}）。 */
  public static <T> Confirmed<T> now(T value, String decidedBy, String evidenceRef) {
    return new Confirmed<>(value, decidedBy, evidenceRef, Instant.now());
  }
}
