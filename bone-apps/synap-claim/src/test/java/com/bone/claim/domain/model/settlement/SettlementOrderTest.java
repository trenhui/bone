package com.bone.claim.domain.model.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A7 结算单单测（资金状态机：阶段单调 + 双人复核 + 终态不可逆向）。
 *
 * <p>关键不变量（§6.4 / §11.7）：阶段单调（SOFT→HARD→REVIEW_FREEZE→PAID，分支单向）； 金额 ≥10000 须两个非第一审核人复核；已终态不可再操作。
 */
class SettlementOrderTest {

  private static final long TENANT = 42L;

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

  @Test
  @DisplayName("建单：SOFT_RESERVED + 追加首条资金流水")
  void create() {
    SettlementOrder order = smallOrder();
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.SOFT_RESERVED);
    assertThat(order.getStatus()).isEqualTo("OPEN");
    assertThat(order.fundingEvents()).hasSize(1);
    assertThat(order.fundingEvents().get(0).stageEnum()).isEqualTo(FundingStage.SOFT_RESERVED);
  }

  @Test
  @DisplayName("小额审核通过：直接 HARD_RESERVED（免双人复核）")
  void auditPassSmall() {
    SettlementOrder order = smallOrder();
    order.auditPass("op1", List.of("r1"), "op1");
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.HARD_RESERVED);
  }

  @Test
  @DisplayName("大额审核通过：REVIEW_FREEZE + 双人复核（两名不同且非第一审核人）")
  void auditPassLargeDualReview() {
    SettlementOrder order = largeOrder();
    order.auditPass("op1", List.of("r2", "r3"), "op1");
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.REVIEW_FREEZE);
  }

  @Test
  @DisplayName("大额双人复核校验：单人 / 与复核人相同第一审核人 → 拒")
  void dualReviewValidation() {
    SettlementOrder order = largeOrder();
    assertThatThrownBy(() -> order.auditPass("op1", List.of("r1"), "op1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("双人复核");
    assertThatThrownBy(() -> order.auditPass("op1", List.of("op1", "r2"), "op1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不得包含第一审核人");
  }

  @Test
  @DisplayName("支付：HARD_RESERVED → PAID，paidAt 落库、状态 CLOSED")
  void pay() {
    SettlementOrder order = smallOrder();
    order.auditPass("op1", List.of("r1"), "op1");
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
        .hasMessageContaining("仅已硬预占/复核冻结状态可支付");
  }

  @Test
  @DisplayName("撤销：→ RELEASED（释放预占）")
  void cancel() {
    SettlementOrder order = smallOrder();
    order.auditPass("op1", List.of("r1"), "op1");
    order.cancel("op2", false);
    assertThat(order.fundingStageEnum()).isEqualTo(FundingStage.RELEASED);
    assertThat(order.fundingEvents()).anyMatch(e -> e.stageEnum() == FundingStage.RELEASED);
  }

  @Test
  @DisplayName("退款前置：仅 PAID 可退款")
  void refundOnlyFromPaid() {
    SettlementOrder order = smallOrder();
    order.auditPass("op1", List.of("r1"), "op1");
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
    order.auditPass("op1", List.of("r1"), "op1");
    order.pay("op2");
    assertThatThrownBy(() -> order.auditPass("op3", List.of("r9"), "op3"))
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
