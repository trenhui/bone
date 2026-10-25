package com.bone.claim.domain.model.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A7 结算单单测（资金状态机：阶段单调 + 大额两次认证 + 终态不可逆向）。
 *
 * <p>关键不变量（§6.4 / §10.3 / §11.7）：阶段单调（SOFT→HARD，大额经 REVIEW_FREEZE 由第二人放行）； ≥1
 * 万的复核人不得是审核发起人、也不得是该赔案的第一审核人；REVIEW_FREEZE 不可直接支付；已终态不可再操作。
 *
 * <p>「两次认证」在类型层面就无从伪造：{@link SettlementOrder#auditPass} 与 {@link SettlementOrder#confirmReview}
 * 都不接受任何名单，身份只能由调用方（应用层）从 token 传入。
 */
class SettlementOrderTest {

  private static final long TENANT = 42L;

  /** 赔案的第一审核人（核赔人）：资金侧审核与复核都不得是他。 */
  private static final String CLAIM_REVIEWER = "claim-reviewer-1";

  private static SettlementOrder create(BigDecimal amount) {
    return SettlementOrder.create(
        9001L, TENANT, "SO1001-1", 1001L, "CL1001", amount, AccountType.PERSONAL, 1);
  }

  private static SettlementOrder smallOrder() {
    return create(new BigDecimal("800.00"));
  }

  private static SettlementOrder largeOrder() {
    return create(new BigDecimal("15000.00"));
  }

  /** 大额单：已由 op1 完成第一道认证，停在 REVIEW_FREEZE。 */
  private static SettlementOrder largeOrderInReviewFreeze() {
    SettlementOrder order = largeOrder();
    order.auditPass("op1", CLAIM_REVIEWER);
    return order;
  }

  @Test
  @DisplayName("建单：SOFT_RESERVED + 追加首条资金流水")
  void createOrder() {
    SettlementOrder order = smallOrder();
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.SOFT_RESERVED);
    assertThat(order.getStatus()).isEqualTo("OPEN");
    assertThat(order.fundingEvents()).hasSize(1);
    assertThat(order.fundingEvents().get(0).stageEnum()).isEqualTo(FundingStage.SOFT_RESERVED);
  }

  @Test
  @DisplayName("小额审核通过：直接 HARD_RESERVED（免第二道认证）")
  void auditPassSmall() {
    SettlementOrder order = smallOrder();
    order.auditPass("op1", CLAIM_REVIEWER);
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.HARD_RESERVED);
    assertThat(order.getReviewFirstApprover()).isNull();
  }

  @Test
  @DisplayName("大额审核通过：REVIEW_FREEZE + 落库审核发起人（第二道认证待办）")
  void auditPassLargeEntersReviewFreeze() {
    SettlementOrder order = largeOrder();
    order.auditPass("op1", CLAIM_REVIEWER);
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.REVIEW_FREEZE);
    assertThat(order.getReviewFirstApprover()).isEqualTo("op1");
    assertThat(order.fundingEvents()).hasSize(2);
  }

  @Test
  @DisplayName("大额审核人=赔案第一审核人 → 拒（既定价又发起放款）")
  void auditPassByClaimReviewerRejected() {
    SettlementOrder order = largeOrder();
    assertThatThrownBy(() -> order.auditPass(CLAIM_REVIEWER, CLAIM_REVIEWER))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不得为该赔案的第一审核人");
  }

  @Test
  @DisplayName("赔案无第一审核人 → 拒（SoD 无从判定，fail-closed）")
  void auditPassWithoutClaimReviewerRejected() {
    SettlementOrder order = largeOrder();
    assertThatThrownBy(() -> order.auditPass("op1", ""))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("未登记第一审核人");
  }

  @Test
  @DisplayName("复核冻结态不可直接支付（第二道认证不可跳过）")
  void payRejectedDuringReviewFreeze() {
    SettlementOrder order = largeOrderInReviewFreeze();
    assertThatThrownBy(() -> order.pay("op2"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("须经第二人复核");
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.REVIEW_FREEZE);
  }

  @Test
  @DisplayName("复核人=审核发起人 → 拒（同一个人的两个马甲不算双人）")
  void confirmReviewBySameOperatorRejected() {
    SettlementOrder order = largeOrderInReviewFreeze();
    assertThatThrownBy(() -> order.confirmReview("op1", CLAIM_REVIEWER))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("复核人不得为本次审核的发起人");
  }

  @Test
  @DisplayName("复核人=赔案第一审核人 → 拒（职责分离）")
  void confirmReviewByClaimReviewerRejected() {
    SettlementOrder order = largeOrderInReviewFreeze();
    assertThatThrownBy(() -> order.confirmReview(CLAIM_REVIEWER, CLAIM_REVIEWER))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不得为该赔案的第一审核人");
  }

  @Test
  @DisplayName("复核人=另一真实身份 → HARD_RESERVED，随后可支付（两次认证台账两条流水）")
  void confirmReviewBySecondIdentity() {
    SettlementOrder order = largeOrderInReviewFreeze();
    order.confirmReview("op2", CLAIM_REVIEWER);
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.HARD_RESERVED);

    order.pay("op3");
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.PAID);
    assertThat(order.fundingEvents())
        .extracting(FundingEvent::getOperator)
        .containsExactly("system", "op1", "op2", "op3");
  }

  @Test
  @DisplayName("非复核冻结态不可复核放行")
  void confirmReviewRequiresReviewFreeze() {
    SettlementOrder order = smallOrder();
    order.auditPass("op1", CLAIM_REVIEWER);
    assertThatThrownBy(() -> order.confirmReview("op2", CLAIM_REVIEWER))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("仅复核冻结");
  }

  @Test
  @DisplayName("复核不通过：REVIEW_FREEZE → RELEASED（释放预占）")
  void reviewRejectedReleasesReservation() {
    SettlementOrder order = largeOrderInReviewFreeze();
    order.cancel("op2", false);
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.RELEASED);
    assertThat(order.fundingEvents()).anyMatch(e -> e.stageEnum() == FundingStage.RELEASED);
  }

  @Test
  @DisplayName("支付：HARD_RESERVED → PAID，paidAt 落库、状态 CLOSED")
  void pay() {
    SettlementOrder order = smallOrder();
    order.auditPass("op1", CLAIM_REVIEWER);
    order.pay("op2");
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.PAID);
    assertThat(order.getPaidAt()).isNotNull();
    assertThat(order.getStatus()).isEqualTo("CLOSED");
  }

  @Test
  @DisplayName("支付前置：非冻结态（SOFT）不可支付")
  void payFromSoftRejected() {
    SettlementOrder order = smallOrder();
    assertThatThrownBy(() -> order.pay("op2"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("仅已硬预占状态可支付");
  }

  @Test
  @DisplayName("撤销：→ RELEASED（释放预占）")
  void cancel() {
    SettlementOrder order = smallOrder();
    order.auditPass("op1", CLAIM_REVIEWER);
    order.cancel("op2", false);
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.RELEASED);
    assertThat(order.fundingEvents()).anyMatch(e -> e.stageEnum() == FundingStage.RELEASED);
  }

  @Test
  @DisplayName("退款前置：仅 PAID 可退款")
  void refundOnlyFromPaid() {
    SettlementOrder order = smallOrder();
    order.auditPass("op1", CLAIM_REVIEWER);
    assertThatThrownBy(() -> order.refund("op2"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("仅 PAID 可退款");

    order.pay("op2");
    order.refund("op3");
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.REFUNDED);
  }

  @Test
  @DisplayName("终态不可逆向：PAID 后再审核 → 拒")
  void terminalGuard() {
    SettlementOrder order = smallOrder();
    order.auditPass("op1", CLAIM_REVIEWER);
    order.pay("op2");
    assertThatThrownBy(() -> order.auditPass("op3", CLAIM_REVIEWER))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("已终态");
  }

  @Test
  @DisplayName("幂等键：{claimNo}:{stage}:{attempt} 与占额周期序号一致")
  void idempotencyKeyShape() {
    SettlementOrder order = smallOrder();
    assertThat(order.fundingEvents().get(0).getIdempotencyKey())
        .isEqualTo("CL1001:SOFT_RESERVED:1");
  }
}
