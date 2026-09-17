package com.bone.blueprint.application.command.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.command.cmd.CloseExpiredPaymentCommand;
import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.valueobject.PaymentChannel;
import com.bone.blueprint.domain.payment.valueobject.PaymentStatus;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CloseExpiredPaymentCommandHandlerTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private TenantProvider tenantProvider;

  @InjectMocks private CloseExpiredPaymentCommandHandler handler;

  private Payment payingPayment() {
    Payment payment =
        Payment.create(
            1L, 1L, 100L, 200L, new BigDecimal("200"), PaymentChannel.SIMULATED, "http://pay");
    payment.submitToChannel("http://pay");
    return payment;
  }

  @Test
  void testClosePayingPayment() {
    Payment payment = payingPayment();
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);

    // 定时任务入口：租户由命令显式携带（异步分支不能依赖线程上下文）
    handler.handle(new CloseExpiredPaymentCommand(1L, 1L));

    assertEquals(PaymentStatus.CLOSED, payment.getStatus());
    verify(paymentRepository).save(payment);
  }

  @Test
  void testCloseSuccessPaymentThrows() {
    Payment payment = payingPayment();
    payment.confirmSuccess("trade-001", new BigDecimal("200")); // SUCCESS 不可关闭
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);

    BizException ex =
        assertThrows(
            BizException.class, () -> handler.handle(new CloseExpiredPaymentCommand(1L, 1L)));
    assertTrue(ex.getMessage().contains("已成功"), ex.getMessage());
    verify(paymentRepository, never()).save(any());
  }

  @Test
  void testPaymentNotFound() {
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(null);

    assertThrows(BizException.class, () -> handler.handle(new CloseExpiredPaymentCommand(1L, 1L)));
    verify(paymentRepository, never()).save(any());
  }

  @Test
  void testTenantFallsBackToContextWhenCommandOmitsIt() {
    // HTTP 入口（tenantId=null）仍走 TenantProvider
    Payment payment = payingPayment();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);

    handler.handle(new CloseExpiredPaymentCommand(1L, null));

    assertEquals(PaymentStatus.CLOSED, payment.getStatus());
  }
}
