package com.bone.claim.domain.model.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 双人复核留痕（§11.7 不变量 5 / §10.3 职责分离）。
 *
 * <p>判的是"库里留下的事实能否证明两次独立认证"，不是"写路径有没有拦住"——后者由 {@code SettlementOrder} 前置拦截。
 * 补数脚本、历史迁移、直接改库都能绕过前置拦截，只有回溯取证看得见。
 */
class ReviewEvidenceTest {

  private static final BigDecimal THRESHOLD = new BigDecimal("10000.00");
  private static final BigDecimal BIG = new BigDecimal("20000.00");
  private static final BigDecimal SMALL = new BigDecimal("800.00");

  @Test
  @DisplayName("小额不判（没到双人复核门，谈不上留痕缺失）")
  void smallAmountNotJudged() {
    assertThat(ReviewEvidence.check(SMALL, THRESHOLD, null, List.of(event("PAID", "op-1"))))
        .isEmpty();
  }

  @Test
  @DisplayName("正常大额：审核人发起复核冻结、另一人放行 ⇒ 无缺口")
  void completeEvidence() {
    List<FundingEvent> events =
        List.of(
            event("SOFT_RESERVED", "system"),
            event("REVIEW_FREEZE", "op-audit"),
            event("HARD_RESERVED", "op-review"),
            event("PAID", "op-review"));

    assertThat(ReviewEvidence.check(BIG, THRESHOLD, "op-audit", events)).isEmpty();
  }

  @Test
  @DisplayName("大额已付款却从未进复核冻结 ⇒ MISSING_REVIEW_EVENT（复核门被绕过）")
  void paidWithoutReviewFreeze() {
    List<FundingEvent> events =
        List.of(
            event("SOFT_RESERVED", "system"),
            event("HARD_RESERVED", "op-audit"),
            event("PAID", "op-audit"));

    assertThat(ReviewEvidence.check(BIG, THRESHOLD, null, events))
        .containsExactly(ReviewEvidence.Gap.MISSING_REVIEW_EVENT);
  }

  @Test
  @DisplayName("大额还在 SOFT（没审核）⇒ 不判（正常在途）")
  void notYetAudited() {
    List<FundingEvent> events = List.of(event("SOFT_RESERVED", "system"));

    assertThat(ReviewEvidence.check(BIG, THRESHOLD, null, events)).isEmpty();
  }

  @Test
  @DisplayName("大额停在复核冻结等第二人 ⇒ 不判（等人不是缺陷）")
  void waitingForSecondApprover() {
    List<FundingEvent> events =
        List.of(event("SOFT_RESERVED", "system"), event("REVIEW_FREEZE", "op-audit"));

    assertThat(ReviewEvidence.check(BIG, THRESHOLD, "op-audit", events)).isEmpty();
  }

  @Test
  @DisplayName("复核冻结后没放行却直接付款 ⇒ MISSING_REVIEW_EVENT（状态机被绕过）")
  void paidWithoutRelease() {
    List<FundingEvent> events =
        List.of(
            event("SOFT_RESERVED", "system"),
            event("REVIEW_FREEZE", "op-audit"),
            event("PAID", "op-audit"));

    assertThat(ReviewEvidence.check(BIG, THRESHOLD, "op-audit", events))
        .containsExactly(ReviewEvidence.Gap.MISSING_REVIEW_EVENT);
  }

  @Test
  @DisplayName("发起人没落库 ⇒ MISSING_FIRST_APPROVER（第二次认证无从比对）")
  void missingFirstApprover() {
    List<FundingEvent> events =
        List.of(
            event("REVIEW_FREEZE", "op-audit"),
            event("HARD_RESERVED", "op-review"),
            event("PAID", "op-review"));

    assertThat(ReviewEvidence.check(BIG, THRESHOLD, "", events))
        .containsExactly(ReviewEvidence.Gap.MISSING_FIRST_APPROVER);
  }

  @Test
  @DisplayName("放行那条没留操作人 ⇒ MISSING_REVIEW_APPROVER")
  void missingReviewer() {
    List<FundingEvent> events =
        List.of(event("REVIEW_FREEZE", "op-audit"), event("HARD_RESERVED", null));

    assertThat(ReviewEvidence.check(BIG, THRESHOLD, "op-audit", events))
        .containsExactly(ReviewEvidence.Gap.MISSING_REVIEW_APPROVER);
  }

  @Test
  @DisplayName("复核人与发起人同一身份 ⇒ SAME_OPERATOR（双人复核退化为一次点击）")
  void sameOperator() {
    List<FundingEvent> events =
        List.of(event("REVIEW_FREEZE", "op-same"), event("HARD_RESERVED", "op-same"));

    assertThat(ReviewEvidence.check(BIG, THRESHOLD, "op-same", events))
        .containsExactly(ReviewEvidence.Gap.SAME_OPERATOR);
  }

  private static FundingEvent event(String stage, String operator) {
    FundingEvent e = mock(FundingEvent.class);
    when(e.stageEnum()).thenReturn(FundingStage.valueOf(stage));
    when(e.getOperator()).thenReturn(operator);
    return e;
  }
}
