package com.bone.blueprint.domain.model.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.blueprint.domain.model.payment.event.PaymentFailedEvent;
import com.bone.blueprint.domain.model.payment.event.PaymentRefundedEvent;
import com.bone.blueprint.domain.model.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.domain.model.payment.valueobject.PaymentChannel;
import com.bone.blueprint.domain.model.payment.valueobject.PaymentStatus;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentTest {

  private Payment createPendingPayment() {
    return Payment.create(
        1L, 1L, 100L, 200L, new BigDecimal("200"), PaymentChannel.SIMULATED, "http://pay");
  }

  @Test
  void testCreateWithInvalidAmount() {
    assertThrows(
        DomainException.class,
        () ->
            Payment.create(
                1L, 1L, 100L, 200L, new BigDecimal("0"), PaymentChannel.SIMULATED, "http://pay"));
  }

  @Test
  void testCreateWithNullRequiredFields() {
    assertThrows(
        DomainException.class,
        () ->
            Payment.create(
                null,
                1L,
                100L,
                200L,
                new BigDecimal("200"),
                PaymentChannel.SIMULATED,
                "http://pay"));
  }

  @Test
  void testSubmitToChannelOnlyFromPending() {
    Payment payment = createPendingPayment();
    payment.submitToChannel("http://pay");
    assertEquals(PaymentStatus.PAYING, payment.getStatus());
    assertEquals("http://pay", payment.getPayUrl());

    // 已 PAYING 再 submitToChannel 抛异常
    assertThrows(DomainException.class, () -> payment.submitToChannel("http://pay"));
  }

  @Test
  void testSubmitToChannelRejectsBlankUrl() {
    Payment payment = createPendingPayment();
    assertThrows(DomainException.class, () -> payment.submitToChannel(null));
    assertThrows(DomainException.class, () -> payment.submitToChannel("  "));
    assertEquals(PaymentStatus.PENDING, payment.getStatus());
  }

  @Test
  void testConfirmSuccessMovesToSuccess() {
    Payment payment = createPendingPayment();
    payment.submitToChannel("http://pay");

    boolean migrated = payment.confirmSuccess("trade-no-001", new BigDecimal("200"));

    assertTrue(migrated);
    assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    assertEquals("trade-no-001", payment.getChannelTradeNo());
    assertNotNull(payment.getPaidAt());
    assertEquals(1, payment.getDomainEvents().size());
    assertInstanceOf(PaymentSucceededEvent.class, payment.getDomainEvents().get(0));
  }

  @Test
  void testConfirmSuccessWithAmountMismatchThrows() {
    Payment payment = createPendingPayment();
    payment.submitToChannel("http://pay");

    // 实付金额与应付金额不一致（部分支付/篡改）→ 拒绝确认
    assertThrows(
        DomainException.class, () -> payment.confirmSuccess("trade-no-001", new BigDecimal("199")));
    assertEquals(PaymentStatus.PAYING, payment.getStatus());
    assertEquals(0, payment.getDomainEvents().size());
  }

  @Test
  void testConfirmSuccessIsIdempotentWithSameTradeNo() {
    Payment payment = createPendingPayment();
    payment.confirmSuccess("trade-no-001", new BigDecimal("200"));
    payment.clearDomainEvents();

    boolean second = payment.confirmSuccess("trade-no-001", new BigDecimal("200"));

    // 幂等：同流水号重复回调不迁移、不抛错、不重复发事件
    assertFalse(second);
    assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    assertEquals("trade-no-001", payment.getChannelTradeNo());
    assertEquals(0, payment.getDomainEvents().size());
  }

  @Test
  void testConfirmSuccessWithDifferentTradeNoThrows() {
    Payment payment = createPendingPayment();
    payment.confirmSuccess("trade-no-001", new BigDecimal("200"));

    // 已成功但回调流水号不同（异常）→ 抛错防止覆盖
    assertThrows(
        DomainException.class, () -> payment.confirmSuccess("trade-no-002", new BigDecimal("200")));
    assertEquals("trade-no-001", payment.getChannelTradeNo());
  }

  @Test
  void testConfirmSuccessOnClosedThrows() {
    Payment payment = createPendingPayment();
    payment.close();

    assertThrows(
        DomainException.class, () -> payment.confirmSuccess("trade-no-001", new BigDecimal("200")));
  }

  @Test
  void testMarkFailedFromPaying() {
    Payment payment = createPendingPayment();
    payment.submitToChannel("http://pay");

    payment.markFailed("trade-no-001");

    assertEquals(PaymentStatus.FAILED, payment.getStatus());
    assertEquals(1, payment.getDomainEvents().size());
    assertInstanceOf(PaymentFailedEvent.class, payment.getDomainEvents().get(0));
  }

  @Test
  void testCloseFromPending() {
    Payment payment = createPendingPayment();
    payment.close();
    assertEquals(PaymentStatus.CLOSED, payment.getStatus());
  }

  @Test
  void testCloseSuccessThrows() {
    Payment payment = createPendingPayment();
    payment.confirmSuccess("trade-no-001", new BigDecimal("200"));

    assertThrows(DomainException.class, payment::close);
  }

  @Test
  void testRefundOnlyFromSuccess() {
    Payment payment = createPendingPayment();
    // 未成功不可退款
    assertThrows(DomainException.class, () -> payment.refund(new BigDecimal("200")));

    payment.confirmSuccess("trade-no-001", new BigDecimal("200"));
    payment.clearDomainEvents();

    boolean refunded = payment.refund(new BigDecimal("200"));

    assertTrue(refunded);
    assertNotNull(payment.getRefundedAt());
    assertEquals(new BigDecimal("200"), payment.getRefundAmount());
    assertEquals(1, payment.getDomainEvents().size());
    assertInstanceOf(PaymentRefundedEvent.class, payment.getDomainEvents().get(0));
  }

  @Test
  void testRefundAmountValidation() {
    Payment payment = createPendingPayment();
    payment.confirmSuccess("trade-no-001", new BigDecimal("200"));

    // 超付金额不可退
    assertThrows(DomainException.class, () -> payment.refund(new BigDecimal("201")));
    // 非正金额不可退
    assertThrows(DomainException.class, () -> payment.refund(new BigDecimal("0")));
    assertThrows(DomainException.class, () -> payment.refund(null));
  }

  @Test
  void testRefundIsIdempotent() {
    Payment payment = createPendingPayment();
    payment.confirmSuccess("trade-no-001", new BigDecimal("200"));
    payment.refund(new BigDecimal("200"));
    payment.clearDomainEvents();

    boolean second = payment.refund(new BigDecimal("200"));

    assertFalse(second);
    assertEquals(0, payment.getDomainEvents().size());
  }

  /** 资金操作不可静默失败：已退款支付单再次收到<strong>金额不同</strong>的退款请求， 必须抛错让调用方感知（很可能是误操作或对账异常）。 */
  @Test
  void testRefundWithDifferentAmountThrows() {
    Payment payment = createPendingPayment();
    payment.confirmSuccess("trade-no-001", new BigDecimal("200"));
    payment.refund(new BigDecimal("50"));

    DomainException ex =
        assertThrows(DomainException.class, () -> payment.refund(new BigDecimal("80")));

    assertTrue(ex.getMessage().contains("不一致"));
    // 原退款记录未被覆盖
    assertEquals(new BigDecimal("50"), payment.getRefundAmount());
  }

  @Test
  void testRefundZeroRejected() {
    Payment payment = createPendingPayment();
    payment.confirmSuccess("trade-no-001", new BigDecimal("200"));

    // 0 元是合法 Money（零元订单），但退 0 元无意义，须拒绝
    DomainException ex =
        assertThrows(DomainException.class, () -> payment.refund(new BigDecimal("0")));

    assertTrue(ex.getMessage().contains("大于0"));
    assertNull(payment.getRefundedAt());
  }

  /** 金额统一走 Money 视图，且与已付金额口径一致。 */
  @Test
  void testAmountMoneyView() {
    Payment payment = createPendingPayment();

    assertEquals(new BigDecimal("200"), payment.getAmountMoney().toBigDecimal());
    assertTrue(payment.getRefundedMoney().isZero());
  }
}
