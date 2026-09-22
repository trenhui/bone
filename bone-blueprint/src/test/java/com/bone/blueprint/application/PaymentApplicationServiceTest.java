package com.bone.blueprint.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.command.RefundPaymentCommand;
import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.domain.model.payment.Payment;
import com.bone.blueprint.domain.model.payment.event.PaymentRefundedEvent;
import com.bone.blueprint.domain.model.payment.valueobject.PaymentChannel;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
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
  @Mock private TenantPort tenantProvider;
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
    when(paymentRepository.findById(1L)).thenReturn(payment);
    doAnswer(
            inv -> {
              Payment saved = inv.getArgument(0);
              if (!saved.getDomainEvents().isEmpty()) {
                eventType.set(saved.getDomainEvents().get(0).getClass());
              }
              return null;
            })
        .when(paymentRepository)
        .update(any(Payment.class));

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
    when(paymentRepository.findById(1L)).thenReturn(payment);

    BizException ex = assertThrows(BizException.class, () -> service.refund(command()));
    assertEquals(409, ex.getCode());
    assertTrue(ex.getMessage().contains("PAYMENT_STATUS_CONFLICT"), ex.getMessage());
  }

  @Test
  void refund_paymentNotFound_throws() {
    when(paymentRepository.findById(1L)).thenReturn(null);

    assertEquals(404, assertThrows(BizException.class, () -> service.refund(command())).getCode());
    verify(paymentRepository, never()).update(any());
  }
}
