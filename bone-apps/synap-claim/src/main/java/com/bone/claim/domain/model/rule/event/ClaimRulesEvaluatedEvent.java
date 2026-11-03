package com.bone.claim.domain.model.rule.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;
import java.util.List;

/**
 * 决策执行完毕、规则命中明细已算定（§9.5 A3 / 推翻率·命中分析·人工确认率的事实源）。
 *
 * <p>由决策图（{@code DecisionGraphExecutor}）在 {@code DecisionExecution} 上挂载、随 {@code
 * DecisionExecutionApplicationService.persist} 的 {@code publishFrom} 发布；{@code
 * ClaimRuleHitRecorder} 订阅后按 {claimId, executionId, ruleCode} 维度 append 一笔 {@code
 * t_claim_rule_hit}。本事件与领域聚合解耦—— 订阅方只关心「这次执行命中了哪些规则」，不关心决策图内部如何求值。
 *
 * <p><b>生产者约定（团队决策图落地时补一行）</b>：在 {@code persist} 之前调用 {@code execution.addDomainEvent(new
 * ClaimRulesEvaluatedEvent(tenantId, claimId, execution.getId(), hits, Instant.now()))}， 其中 {@code
 * hits} 由决策图求值阶段的规则命中明细映射而来（ruleCode / ruleVersion / ruleCategory / priority / inputSnapshot /
 * conclusion / hitAt，与 {@code ClaimRuleHit.create} 入参一一对应）；{@code executionId} 即 {@code
 * DecisionExecution.start} 传入的 id。
 */
public record ClaimRulesEvaluatedEvent(
    Long tenantId, Long claimId, Long executionId, List<RuleHit> hits, Instant occurredAt)
    implements DomainEvent {

  /** 单条规则命中明细（与 {@code ClaimRuleHit.create} 入参一一对应）。 */
  public record RuleHit(
      String ruleCode,
      String ruleVersion,
      String ruleCategory,
      Integer priority,
      String inputSnapshot,
      String conclusion,
      Instant hitAt) {}
}
