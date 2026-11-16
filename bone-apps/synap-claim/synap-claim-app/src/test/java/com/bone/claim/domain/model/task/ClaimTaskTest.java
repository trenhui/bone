package com.bone.claim.domain.model.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 任务聚合法行为（任务 2.3／2.4 的领域侧判据）。
 *
 * <p>这里只钉领域规则（状态机 + 不臆造归属）。「并发恰 1 成功」的数据库侧由乐观锁与生成列唯一键保证， 其探针见验证报告；本类钉的是「不该允许的跃迁必须抛
 * DomainException」——若领域放行， 数据库层再怎么保证唯一也没用（重复领取会被当成合法更新）。
 */
class ClaimTaskTest {

  private static final Instant NOW = Instant.parse("2026-11-16T02:00:00Z");
  private static final Instant LATER = NOW.plusSeconds(60);

  private static TaskAssignmentContext ctx() {
    return new TaskAssignmentContext(
        TaskType.INITIAL_REVIEW, "OUTPATIENT", "APP", new BigDecimal("1200.00"));
  }

  private static ClaimTask openTask() {
    return ClaimTask.open(1L, 10L, "T1", 100L, ctx(), 100, NOW);
  }

  private static ClaimAssignRule rule(long id, String assigneeId) {
    return ClaimAssignRule.create(
        id,
        10L,
        "r" + id,
        TaskType.INITIAL_REVIEW.name(),
        "",
        "",
        null,
        null,
        AssigneeType.USER.name(),
        assigneeId,
        null,
        10,
        true,
        NOW);
  }

  @Test
  @DisplayName("新建任务为入池形态：PENDING_CLAIM 且无归属人（默认安全形态，不会凭空消失）")
  void open_isPoolShaped() {
    ClaimTask t = openTask();

    assertThat(t.getStatus()).isEqualTo(ClaimTaskStatus.PENDING_CLAIM.name());
    assertThat(t.getAssigneeId()).isNull();
    assertThat(t.getAssigneeType()).isNull();
    assertThat(t.isActive()).isTrue();
    assertThat(t.getClaimAmount()).isEqualByComparingTo("1200.00");
  }

  @Test
  @DisplayName("入池结论只写理由码，不写任何 assignee（assign_reason 不是归属）")
  void applyPool_writesReasonOnly() {
    ClaimTask t = openTask();

    t.applyAssignment(AssignmentDecision.pool(AssignReason.ALL_INELIGIBLE), LATER);

    assertThat(t.getStatus()).isEqualTo(ClaimTaskStatus.PENDING_CLAIM.name());
    assertThat(t.getAssigneeId()).isNull();
    assertThat(t.getAssignReason()).isEqualTo(AssignReason.ALL_INELIGIBLE.name());
    assertThat(t.getAssignedAt()).isNull();
  }

  @Test
  @DisplayName("命中规则：写入 assignee + ruleId + assignedAt，状态转 ASSIGNED")
  void applyMatched_assigns() {
    ClaimTask t = openTask();

    t.applyAssignment(AssignmentDecision.matched(rule(7L, "u-7")), LATER);

    assertThat(t.getStatus()).isEqualTo(ClaimTaskStatus.ASSIGNED.name());
    assertThat(t.getAssigneeId()).isEqualTo("u-7");
    assertThat(t.getAssigneeType()).isEqualTo(AssigneeType.USER.name());
    assertThat(t.getAssignRuleId()).isEqualTo(7L);
    assertThat(t.getAssignReason()).isEqualTo(AssignReason.RULE_MATCH.name());
    assertThat(t.getAssignedAt()).isEqualTo(LATER);
  }

  @Test
  @DisplayName("已指派/已领取的任务不可再应用分配结论（防止重复分配覆盖既有归属）")
  void applyTwice_rejected() {
    ClaimTask t = openTask();
    t.applyAssignment(AssignmentDecision.matched(rule(7L, "u-7")), LATER);

    assertThatThrownBy(() -> t.applyAssignment(AssignmentDecision.matched(rule(8L, "u-8")), LATER))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("入池任务");
  }

  @Test
  @DisplayName("自领：入池任务被领取后转 PROCESSING，归属改为领取人本人")
  void claim_fromPool_movesToProcessing() {
    ClaimTask t = openTask();

    t.claim("u-9", LATER);

    assertThat(t.getStatus()).isEqualTo(ClaimTaskStatus.PROCESSING.name());
    assertThat(t.getAssigneeId()).isEqualTo("u-9");
    assertThat(t.getAssigneeType()).isEqualTo(AssigneeType.USER.name());
    assertThat(t.getClaimedAt()).isEqualTo(LATER);
  }

  @Test
  @DisplayName("重复领取：已 PROCESSING 的任务再被领取必须抛异常（两人同时处理会产生两份互相覆盖的理算结论）")
  void claim_alreadyProcessing_rejected() {
    ClaimTask t = openTask();
    t.claim("u-9", LATER);

    assertThatThrownBy(() -> t.claim("u-10", LATER))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不可重复领取");
  }

  @Test
  @DisplayName("指派给他人的任务不可被第三人领取（否则「指派」形同虚设）")
  void claim_assignedToOther_rejected() {
    ClaimTask t = openTask();
    t.applyAssignment(AssignmentDecision.matched(rule(7L, "u-7")), LATER);

    assertThatThrownBy(() -> t.claim("u-8", LATER))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("已指派给他人");
  }

  @Test
  @DisplayName("被指派给本人时可以领取")
  void claim_assignedToSelf_allowed() {
    ClaimTask t = openTask();
    t.applyAssignment(AssignmentDecision.matched(rule(7L, "u-7")), LATER);

    t.claim("u-7", LATER.plusSeconds(1));

    assertThat(t.getStatus()).isEqualTo(ClaimTaskStatus.PROCESSING.name());
  }

  @Test
  @DisplayName("终态不可领取、不可再取消/完成；完成与取消互斥")
  void terminalStates_areFinal() {
    ClaimTask done = openTask();
    done.claim("u-9", LATER);
    done.complete(LATER.plusSeconds(1));

    assertThat(done.getStatus()).isEqualTo(ClaimTaskStatus.DONE.name());
    assertThat(done.isActive()).isFalse();
    assertThatThrownBy(() -> done.claim("u-9", LATER)).hasMessageContaining("终态");
    assertThatThrownBy(() -> done.cancel(LATER)).hasMessageContaining("不能取消");

    ClaimTask cancelled = openTask();
    cancelled.cancel(LATER);
    assertThat(cancelled.getStatus()).isEqualTo(ClaimTaskStatus.CANCELLED.name());
    assertThatThrownBy(() -> cancelled.complete(LATER)).hasMessageContaining("已取消");
  }

  @Test
  @DisplayName("生效状态集合与生成列口径一致：只有 PENDING_CLAIM/ASSIGNED/PROCESSING 占唯一名额")
  void activeNames_matchGeneratedColumn() {
    assertThat(ClaimTaskStatus.activeNames())
        .containsExactlyInAnyOrder("PENDING_CLAIM", "ASSIGNED", "PROCESSING");
    assertThat(List.of(ClaimTaskStatus.DONE, ClaimTaskStatus.CANCELLED))
        .allMatch(ClaimTaskStatus::isTerminal);
  }
}
