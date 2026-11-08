package com.bone.claim.domain.model.settlement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 大额双人复核的<b>留痕</b>校验（§11.7 不变量 5，§10.3 职责分离）。
 *
 * <p><b>聚合已经拦住了写路径，为什么还要巡检</b>：{@code SettlementOrder#confirmReview} 强制"复核人 ≠ 审核发起人 ≠
 * 赔案第一审核人"，但它只在<b>写入那一刻</b>生效——补数脚本、历史数据迁移、状态机外的直接改库都能绕过。 巡检判的是"<b>库里留下的事实</b>是否构成两次独立认证"，两者不是同一件事：
 * 前者防未来，后者证明过去。
 *
 * <p><b>证据在哪里</b>：两条 {@code settle_funding_event} 才是"两次认证"的台账——{@code REVIEW_FREEZE} 由审核人推进并 落
 * {@code operator}，其后第一条 {@code HARD_RESERVED} 由复核人推进并落 {@code operator}； {@code
 * settle_order.review_first_approver} 是发起人（复核时需要与它比对，因为 {@code findById} 不回填级联集合）。 只看其中任何一处都判不出一致性。
 *
 * <p><b>刻意不判"复核人 ≠ 赔案第一审核人"</b>：那是 {@code claim_case.first_reviewer_id}，会随案件后续审核/复核操作被改写，
 * 回溯判会把大量正常案件判成违规（现值早已不是当初那次结算时的人）。这一条由聚合在写路径上拦，巡检不重复判—— 宁可少判一条，也不要造出一片没人看的假红。
 */
public final class ReviewEvidence {

  /** 留痕缺口类型（差异单 {@code remote_status} 直接承载枚举名）。 */
  public enum Gap {
    /** 大额结算已过审核，却没落 {@code review_first_approver}（第二次认证无从比对）。 */
    MISSING_FIRST_APPROVER,
    /** 大额结算已付款，但流水里根本没有 {@code REVIEW_FREEZE}——复核门被绕过。 */
    MISSING_REVIEW_EVENT,
    /** 有 {@code REVIEW_FREEZE} 且已放行，但放行那条 {@code HARD_RESERVED} 没留操作人。 */
    MISSING_REVIEW_APPROVER,
    /** 复核放行人与审核发起人是同一个身份（双人复核退化为一次点击）。 */
    SAME_OPERATOR
  }

  private ReviewEvidence() {}

  /**
   * 判某张结算单的双人复核留痕是否完整（空集 = 完整，或本就不需要双人复核）。
   *
   * @param amount 结算金额
   * @param threshold 触发双人复核的金额门（<b>必须传入聚合的常量</b>，不要在对账侧另写一个 10000——阈值两处各写一遍， 改一处就静默失效）
   * @param reviewFirstApprover 审核发起人（{@code settle_order.review_first_approver}）
   * @param events 该结算单的资金流水（按落账顺序）
   */
  public static List<Gap> check(
      BigDecimal amount,
      BigDecimal threshold,
      String reviewFirstApprover,
      List<FundingEvent> events) {
    List<Gap> gaps = new ArrayList<>();
    if (amount == null || threshold == null || amount.compareTo(threshold) < 0) {
      return gaps; // 小额：无双人复核要求，不判
    }
    List<FundingEvent> chain = events == null ? List.of() : events;
    if (!hasStage(chain, FundingStage.REVIEW_FREEZE)) {
      // 大额却从未进复核冻结：还没审核（仍在 SOFT）属正常在途，不判；已经付款才是绕过复核门
      if (hasStage(chain, FundingStage.PAID)) {
        gaps.add(Gap.MISSING_REVIEW_EVENT);
      }
      return gaps;
    }
    if (reviewFirstApprover == null || reviewFirstApprover.isBlank()) {
      gaps.add(Gap.MISSING_FIRST_APPROVER);
    }
    FundingEvent release =
        firstAfter(chain, FundingStage.REVIEW_FREEZE, FundingStage.HARD_RESERVED);
    if (release == null) {
      // 复核冻结之后还没放行：可能正在等第二人（正常），也可能已经退款/撤销（那不需要放行）
      if (paidAfter(chain, FundingStage.REVIEW_FREEZE)) {
        gaps.add(Gap.MISSING_REVIEW_EVENT); // 没放行却付了款 = 状态机被绕过
      }
      return gaps;
    }
    String reviewer = release.getOperator();
    if (reviewer == null || reviewer.isBlank()) {
      gaps.add(Gap.MISSING_REVIEW_APPROVER);
      return gaps;
    }
    if (reviewer.equals(reviewFirstApprover)) {
      gaps.add(Gap.SAME_OPERATOR);
    }
    return gaps;
  }

  private static boolean hasStage(List<FundingEvent> events, FundingStage stage) {
    return events.stream().anyMatch(e -> e.stageEnum() == stage);
  }

  /** 指定阶段之后第一条目标阶段的事件（没有则 {@code null}）。 */
  private static FundingEvent firstAfter(
      List<FundingEvent> events, FundingStage from, FundingStage target) {
    boolean seen = false;
    for (FundingEvent event : events) {
      if (event.stageEnum() == from) {
        seen = true;
        continue;
      }
      if (seen && event.stageEnum() == target) {
        return event;
      }
    }
    return null;
  }

  private static boolean paidAfter(List<FundingEvent> events, FundingStage from) {
    return firstAfter(events, from, FundingStage.PAID) != null;
  }
}
