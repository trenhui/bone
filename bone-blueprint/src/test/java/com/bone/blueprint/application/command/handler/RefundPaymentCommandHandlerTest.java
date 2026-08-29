package com.bone.blueprint.application.command.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.command.cmd.RefundPaymentCommand;
import com.bone.blueprint.domain.gateway.AggregatePersister;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.event.PaymentRefundedEvent;
import com.bone.blueprint.domain.payment.valueobject.PaymentChannel;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.blueprint.infrastructure.persistence.AggregatePersistence;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.DomainException;
import com.bone.core.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundPaymentCommandHandlerTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private TenantProvider tenantProvider;
  @Spy private AggregatePersister aggregatePersister = new AggregatePersistence();
  @Mock private DomainEventPublisher domainEventPublisher;

  @InjectMocks private RefundPaymentCommandHandler handler;

  private RefundPaymentCommand command() {
    return new RefundPaymentCommand(1L, new BigDecimal("200"));
  }

  @Test
  void testRefundSuccess() {
    Payment payment =
        Payment.create(
            1L, 1L, 100L, 200L, new BigDecimal("200"), PaymentChannel.SIMULATED, "http://pay");
    payment.confirmSuccess("trade-001", new BigDecimal("200"));
    payment.clearDomainEvents(); // 清除支付成功事件，聚焦退款事件

    // 保存时快照聚合已挂载的事件（AggregatePersistence 保存后会 clearDomainEvents，须在 save 时捕获）
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

    handler.handle(command());

    assertNotNull(payment.getRefundedAt());
    assertEquals(new BigDecimal("200"), payment.getRefundAmount());
    assertEquals(PaymentRefundedEvent.class, eventType.get());
  }

  @Test
  void testRefundNotSuccessThrows() {
    Payment payment =
        Payment.create(
            1L, 1L, 100L, 200L, new BigDecimal("200"), PaymentChannel.SIMULATED, "http://pay");
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);

    // 未成功支付单不可退款
    assertThrows(DomainException.class, () -> handler.handle(command()));
    verify(paymentRepository, never()).save(any());
  }

  @Test
  void testRefundPaymentNotFound() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> handler.handle(command()));
    verify(paymentRepository, never()).save(any());
  }
}
