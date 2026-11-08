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
 * <p><b>生产者（已落地，G2-1）</b>：{@code QlExpressRuleEnginePortAdapter} 产出 {@code RuleEnginePort.RuleHit}
 * → {@code RuleCheckNodeHandler} 输出到决策图 {@code outputs} → {@code DecisionGraphExecutor} 映射为 {@code
 * RuleHit} 列表后调用 {@code DecisionExecution.recordRuleHits(hits)}， 由聚合在 {@code persist}
 * 前内聚发布本事件（空命中不发）。基类 {@code addDomainEvent} 为 {@code protected}， 应用层不可直接调用，故只能经 {@code
 * recordRuleHits} 发布。{@code executionId} 即 {@code DecisionExecution.start} 传入的 id；每条 {@code
 * RuleHit} 字段与 {@code ClaimRuleHit.create} 入参一一对应。
 *
 * <p><b>口径待拍板</b>：{@code ruleCategory} 目前由规则引擎产出裸类别码（{@code FRAUD/EVIDENCE/AMOUNT}）， 而 DDL 注释与
 * {@code ClaimRuleHit} 常量登记的是 {@code R-AREA/.../R-FRAUD} 九个 {@code R-} 前缀码。 两侧尚未收敛（写入不报错，但按 {@code
 * R-FRAUD} 归因会查不到 {@code FRAUD}）；收敛须由产品/架构定归属， 消费侧不擅自改写，避免造出第三种口径。
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
