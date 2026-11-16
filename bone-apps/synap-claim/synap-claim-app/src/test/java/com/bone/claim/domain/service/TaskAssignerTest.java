package com.bone.claim.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.task.AssignReason;
import com.bone.claim.domain.model.task.AssigneeType;
import com.bone.claim.domain.model.task.AssignmentDecision;
import com.bone.claim.domain.model.task.ClaimAssignRule;
import com.bone.claim.domain.model.task.TaskAssignmentContext;
import com.bone.claim.domain.model.task.TaskType;
import com.bone.claim.domain.port.out.AssigneeEligibilityPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 规则求解与候选打分（任务 2.2 的判据钉子）。
 *
 * <p>覆盖四类判据：命中 / 候选集全不可用 / 优先级与固定顺序 / 金额段边界。 其中「固定顺序」用<b>打乱入参顺序</b>的互补用例验证——只断言"结果等于某条规则"是不够的，
 * 那在集合序恰好正确时会假绿；必须证明同一规则集在任意入参顺序下结论一致。
 */
class TaskAssignerTest {

  private static final Instant NOW = Instant.parse("2026-11-16T02:00:00Z");

  /** 全部可用（真实适配器在同模块另有实现，这里只隔离求解逻辑）。 */
  private static final AssigneeEligibilityPort ALL_ELIGIBLE = (type, id) -> true;

  /** 全部不可用（模拟 IAM 全挂 / 目标全部失效）。 */
  private static final AssigneeEligibilityPort NONE_ELIGIBLE = (type, id) -> false;

  private static ClaimAssignRule rule(
      long id,
      int priority,
      String claimType,
      String channel,
      String min,
      String max,
      String assigneeId) {
    return ClaimAssignRule.create(
        id,
        1L,
        "rule-" + id,
        TaskType.INITIAL_REVIEW.name(),
        claimType,
        channel,
        min == null ? null : new BigDecimal(min),
        max == null ? null : new BigDecimal(max),
        AssigneeType.USER.name(),
        assigneeId,
        null,
        priority,
        true,
        NOW);
  }

  private static TaskAssignmentContext ctx(String claimType, String amount) {
    return new TaskAssignmentContext(
        TaskType.INITIAL_REVIEW, claimType, "APP", amount == null ? null : new BigDecimal(amount));
  }

  @Test
  @DisplayName("命中：唯一匹配规则被选中，理由码 RULE_MATCH 且带回规则")
  void decide_onlyMatchingRule_isAssigned() {
    List<ClaimAssignRule> rules =
        List.of(
            rule(1, 100, "OUTPATIENT", "", null, null, "u1"),
            rule(2, 100, "INPATIENT", "", null, null, "u2"));

    AssignmentDecision d = TaskAssigner.decide(ctx("INPATIENT", "500"), rules, ALL_ELIGIBLE);

    assertThat(d.assigned()).isTrue();
    assertThat(d.reason()).isEqualTo(AssignReason.RULE_MATCH);
    assertThat(d.rule().getId()).isEqualTo(2L);
    assertThat(d.rule().getAssigneeId()).isEqualTo("u2");
  }

  @Test
  @DisplayName("回落：规则表为空 → NO_RULE 入池，不抛异常（新租户上线即无规则）")
  void decide_noRules_poolsWithoutThrowing() {
    AssignmentDecision d = TaskAssigner.decide(ctx("OUTPATIENT", "100"), List.of(), ALL_ELIGIBLE);

    assertThat(d.assigned()).isFalse();
    assertThat(d.reason()).isEqualTo(AssignReason.NO_RULE);
    assertThat(d.rule()).isNull();
  }

  @Test
  @DisplayName("回落：规则全不匹配 → NO_RULE（不是 ALL_INELIGIBLE——没有候选，不是候选不可用）")
  void decide_noMatch_reportsNoRule() {
    AssignmentDecision d =
        TaskAssigner.decide(
            ctx("OUTPATIENT", "100"),
            List.of(rule(1, 100, "INPATIENT", "", null, null, "u1")),
            ALL_ELIGIBLE);

    assertThat(d.assigned()).isFalse();
    assertThat(d.reason()).isEqualTo(AssignReason.NO_RULE);
  }

  @Test
  @DisplayName("候选集全不可用 → ALL_INELIGIBLE 入池：绝不臆造归属")
  void decide_allCandidatesIneligible_poolsWithReason() {
    List<ClaimAssignRule> rules = List.of(rule(1, 100, "", "", null, null, "u1"));

    AssignmentDecision d = TaskAssigner.decide(ctx("OUTPATIENT", "100"), rules, NONE_ELIGIBLE);

    assertThat(d.assigned()).isFalse();
    assertThat(d.reason()).isEqualTo(AssignReason.ALL_INELIGIBLE);
    assertThat(d.rule()).isNull();
  }

  @Test
  @DisplayName("候选全不可用时继续尝试下一个可用候选（不是首条不可用即放弃）")
  void decide_skipsIneligibleAndUsesNextCandidate() {
    List<ClaimAssignRule> rules =
        List.of(
            rule(1, 10, "", "", null, null, "u-dead"), rule(2, 20, "", "", null, null, "u-alive"));
    AssigneeEligibilityPort onlySecond = (type, id) -> "u-alive".equals(id);

    AssignmentDecision d = TaskAssigner.decide(ctx("OUTPATIENT", "100"), rules, onlySecond);

    assertThat(d.assigned()).isTrue();
    assertThat(d.rule().getId()).isEqualTo(2L);
  }

  @Test
  @DisplayName("优先级：priority 数值小者先命中（同维度下高优先级覆盖低优先级）")
  void decide_priorityAscWins() {
    List<ClaimAssignRule> rules =
        List.of(
            rule(9, 200, "", "", null, null, "u-low"), rule(8, 5, "", "", null, null, "u-high"));

    AssignmentDecision d = TaskAssigner.decide(ctx("OUTPATIENT", "100"), rules, ALL_ELIGIBLE);

    assertThat(d.rule().getId()).isEqualTo(8L);
    assertThat(d.rule().getAssigneeId()).isEqualTo("u-high");
  }

  @Test
  @DisplayName("固定顺序：同优先级时按 id 升序，且结论与入参顺序无关（打乱三种顺序结论一致）")
  void decide_samePriority_tiesBrokenByIdDeterministically() {
    ClaimAssignRule a = rule(11, 50, "", "", null, null, "u-a");
    ClaimAssignRule b = rule(12, 50, "", "", null, null, "u-b");
    ClaimAssignRule c = rule(13, 50, "", "", null, null, "u-c");

    List<List<ClaimAssignRule>> permutations = new ArrayList<>();
    permutations.add(List.of(a, b, c));
    permutations.add(List.of(c, a, b));
    permutations.add(List.of(b, c, a));

    for (List<ClaimAssignRule> input : permutations) {
      AssignmentDecision d = TaskAssigner.decide(ctx("OUTPATIENT", "100"), input, ALL_ELIGIBLE);
      assertThat(d.rule().getId()).as("入参顺序 " + ids(input)).isEqualTo(11L);
    }
  }

  @Test
  @DisplayName("金额段左闭右开：1000 命中 [1000,5000)，不命中 [0,1000)")
  void decide_amountRange_isHalfOpen() {
    List<ClaimAssignRule> rules =
        List.of(
            rule(1, 10, "", "", "0", "1000", "u-small"),
            rule(2, 20, "", "", "1000", "5000", "u-mid"));

    assertThat(TaskAssigner.decide(ctx("OUTPATIENT", "1000"), rules, ALL_ELIGIBLE).rule().getId())
        .isEqualTo(2L);
    assertThat(TaskAssigner.decide(ctx("OUTPATIENT", "999.99"), rules, ALL_ELIGIBLE).rule().getId())
        .isEqualTo(1L);
    assertThat(TaskAssigner.decide(ctx("OUTPATIENT", "5000"), rules, ALL_ELIGIBLE).assigned())
        .isFalse();
  }

  @Test
  @DisplayName("金额未知不命中设了金额段的规则（null 不得当 0）：大额专属组不会静默吞下金额缺失案件")
  void decide_nullAmount_doesNotMatchAmountScopedRule() {
    List<ClaimAssignRule> rules = List.of(rule(1, 10, "", "", "0", "1000", "u-small"));

    AssignmentDecision d = TaskAssigner.decide(ctx("OUTPATIENT", null), rules, ALL_ELIGIBLE);

    assertThat(d.assigned()).isFalse();
    assertThat(d.reason()).isEqualTo(AssignReason.NO_RULE);
  }

  @Test
  @DisplayName("目标为空的规则不可命中（导入不全的规则不得产出空 assignee 的已指派任务）")
  void decide_blankAssignee_ruleIsSkipped() {
    List<ClaimAssignRule> rules = List.of(rule(1, 10, "", "", null, null, "   "));

    AssignmentDecision d = TaskAssigner.decide(ctx("OUTPATIENT", "100"), rules, ALL_ELIGIBLE);

    assertThat(d.assigned()).isFalse();
    assertThat(d.reason()).isEqualTo(AssignReason.ALL_INELIGIBLE);
  }

  @Test
  @DisplayName("停用规则不参与求解（enabled=false 即使维度全匹配也不命中）")
  void decide_disabledRule_ignored() {
    ClaimAssignRule disabled =
        ClaimAssignRule.create(
            1L,
            1L,
            "disabled",
            "*",
            "",
            "",
            null,
            null,
            AssigneeType.USER.name(),
            "u1",
            null,
            1,
            false,
            NOW);

    AssignmentDecision d =
        TaskAssigner.decide(ctx("OUTPATIENT", "100"), List.of(disabled), ALL_ELIGIBLE);

    assertThat(d.assigned()).isFalse();
    assertThat(d.reason()).isEqualTo(AssignReason.NO_RULE);
  }

  @Test
  @DisplayName("任务类型通配 * 命中任意类型；空维度＝不限制")
  void decide_wildcardsMatch() {
    ClaimAssignRule wildcard =
        ClaimAssignRule.create(
            1L,
            1L,
            "all",
            "*",
            "",
            "",
            null,
            null,
            AssigneeType.DEPT.name(),
            "dept-1",
            "dept-1",
            1,
            true,
            NOW);

    AssignmentDecision d =
        TaskAssigner.decide(
            new TaskAssignmentContext(TaskType.SECOND_REVIEW, null, null, null),
            List.of(wildcard),
            ALL_ELIGIBLE);

    assertThat(d.assigned()).isTrue();
    assertThat(d.rule().getAssigneeType()).isEqualTo(AssigneeType.DEPT.name());
  }

  private static List<Long> ids(List<ClaimAssignRule> rules) {
    return rules.stream().map(ClaimAssignRule::getId).toList();
  }
}
