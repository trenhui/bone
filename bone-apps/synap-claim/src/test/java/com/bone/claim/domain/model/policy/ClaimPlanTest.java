package com.bone.claim.domain.model.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 保障计划 / 险种聚合根单测（配置面）。 */
class ClaimPlanTest {

  private ClaimPlan newPlan() {
    return ClaimPlan.draft(
        10L, 0L, "POL-001", "PLAN-001", "门诊计划", new BigDecimal("100000.00"), 30, "v1");
  }

  @Test
  @DisplayName("新建计划为草稿态（草稿计划下的责任不参与理算）")
  void draftStatus() {
    assertThat(newPlan().status()).isEqualTo(ClaimPlan.STATUS_DRAFT);
    assertThat(newPlan().isActive()).isFalse();
  }

  @Test
  @DisplayName("计划额度为负 → 拒绝")
  void negativeLimitRejected() {
    assertThatThrownBy(
            () ->
                ClaimPlan.draft(10L, 0L, "POL-001", "PLAN-001", "x", new BigDecimal("-1"), 0, "v1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("计划额度");
  }

  @Test
  @DisplayName("等待期为负 → 拒绝")
  void negativeWaitingPeriodRejected() {
    assertThatThrownBy(
            () -> ClaimPlan.draft(10L, 0L, "POL-001", "PLAN-001", "x", BigDecimal.TEN, -1, "v1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("等待期");
  }

  @Test
  @DisplayName("发布 → ACTIVE 且发领域事件")
  void publishActivates() {
    ClaimPlan plan = newPlan();
    plan.publish("op-1");

    assertThat(plan.status()).isEqualTo(ClaimPlan.STATUS_ACTIVE);
    assertThat(plan.isActive()).isTrue();
    assertThat(plan.getDomainEvents()).hasSize(1);
    assertThat(plan.getDomainEvents().get(0))
        .isInstanceOf(com.bone.claim.domain.model.policy.event.PlanStatusChangedEvent.class);
  }

  @Test
  @DisplayName("已下线计划不可再发布（否则历史案件的计算口径会被追溯改写）")
  void retiredPlanCannotBePublished() {
    ClaimPlan plan = newPlan();
    plan.retire("op-1");

    assertThatThrownBy(() -> plan.publish("op-2"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("已下线");
  }

  @Test
  @DisplayName("重复发布 → 幂等（不重复发事件）")
  void publishIsIdempotent() {
    ClaimPlan plan = newPlan();
    plan.publish("op-1");
    plan.publish("op-2");

    assertThat(plan.getDomainEvents()).hasSize(1);
  }

  @Test
  @DisplayName("险种责任形式非法 → 拒绝（写不进引擎认识的形态）")
  void invalidLiabilityTypeRejected() {
    assertThatThrownBy(
            () -> ClaimCoverage.draft(20L, 0L, 10L, "COV-001", "门诊", BigDecimal.TEN, "WEIRD_TYPE"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("责任形式");
  }

  @Test
  @DisplayName("险种额度为负 → 拒绝")
  void negativeCoverageLimitRejected() {
    assertThatThrownBy(
            () ->
                ClaimCoverage.draft(
                    20L, 0L, 10L, "COV-001", "门诊", new BigDecimal("-1"), "REIMBURSEMENT"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("险种额度");
  }
}
