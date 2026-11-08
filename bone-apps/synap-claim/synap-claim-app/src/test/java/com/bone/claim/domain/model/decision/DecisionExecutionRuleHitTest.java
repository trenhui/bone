package com.bone.claim.domain.model.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.rule.event.ClaimRulesEvaluatedEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * G2-1 留痕链路的聚合侧验证：{@code addDomainEvent} 是 protected，应用层不可直接调用， 故由 {@link
 * DecisionExecution#recordRuleHits} 内聚发布——这是决策图 TODO 能落地的前提。
 */
class DecisionExecutionRuleHitTest {

  private static DecisionExecution execution() {
    return DecisionExecution.start(
        9001L,
        42L,
        "trace-g2-1",
        1001L,
        "graph-v1",
        "rule-v1",
        "model-v1",
        "prompt-v1",
        "catalog-v1");
  }

  @Test
  @DisplayName("G2-1：聚合发布规则命中事件，租户/案件/执行 ID 可回放")
  void recordRuleHitsPublishesEvent() {
    DecisionExecution execution = execution();
    var hit =
        new ClaimRulesEvaluatedEvent.RuleHit(
            "R-FRAUD-06",
            "claim-ruleset-v1",
            "FRAUD",
            10,
            "{}",
            "VERIFY_NOT_PASSED",
            Instant.now());

    execution.recordRuleHits(List.of(hit));

    assertThat(execution.getDomainEvents()).hasSize(1);
    assertThat(execution.getDomainEvents().get(0)).isInstanceOf(ClaimRulesEvaluatedEvent.class);
    var event = (ClaimRulesEvaluatedEvent) execution.getDomainEvents().get(0);
    assertThat(event.tenantId()).isEqualTo(42L);
    assertThat(event.claimId()).isEqualTo(1001L);
    assertThat(event.executionId()).isEqualTo(9001L);
    assertThat(event.hits()).containsExactly(hit);
  }

  @Test
  @DisplayName("空命中不发事件（recorder 无副作用，也不污染审计台账）")
  void emptyHitsPublishNothing() {
    DecisionExecution execution = execution();

    execution.recordRuleHits(List.of());
    execution.recordRuleHits(null);

    assertThat(execution.getDomainEvents()).isEmpty();
  }
}
