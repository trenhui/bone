package com.bone.blueprint.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.command.cmd.RefundPaymentCommand;
import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.event.PaymentRefundedEvent;
import com.bone.blueprint.domain.payment.valueobject.PaymentChannel;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link PaymentApplicationService} — refund 用例单元测试。 */
@ExtendWith(MockitoExtension.class)
class PaymentApplicationServiceTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private TenantProvider tenantProvider;
  @Mock private DomainEventPublisher domainEventPublisher;

  @InjectMocks private PaymentApplicationService service;

  private RefundPaymentCommand command() {
    return new RefundPaymentCommand(1L, new BigDecimal("200"));
  }

  @Test
  void refund_success() {
    Payment payment =
        Payment.create(
            1L, 1L, 100L, 200L, new BigDecimal("200"), PaymentChannel.SIMULATED, "http://pay");
    payment.confirmSuccess("trade-001", new BigDecimal("200"));
    payment.clearDomainEvents();

    AtomicReference<Class<?>> eventType = new AtomicReference<>();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(
            inv -> {
              Payment saved = inv.getArgument(0);
              if (!saved.getDomainEvents().isEmpty()) {
                eventType.set(saved.getDomainEvents().get(0).getClass());
              }
              return 1L;
            });

    service.refund(command());

    assertNotNull(payment.getRefundedAt());
    assertEquals(new BigDecimal("200"), payment.getRefundAmount());
    assertEquals(PaymentRefundedEvent.class, eventType.get());
  }

  @Test
  void refund_notSuccess_throws() {
    Payment payment =
        Payment.create(
            1L, 1L, 100L, 200L, new BigDecimal("200"), PaymentChannel.SIMULATED, "http://pay");
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);

    assertThrows(DomainException.class, () -> service.refund(command()));
    verify(paymentRepository, never()).save(any());
  }

  @Test
  void refund_paymentNotFound_throws() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(null);

    assertEquals(404, assertThrows(BizException.class, () -> service.refund(command())).getCode());
    verify(paymentRepository, never()).save(any());
  }
}
