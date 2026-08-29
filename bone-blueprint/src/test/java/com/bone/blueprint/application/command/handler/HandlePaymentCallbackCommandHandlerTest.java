package com.bone.blueprint.application.command.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.command.cmd.HandlePaymentCallbackCommand;
import com.bone.blueprint.domain.gateway.AggregatePersister;
import com.bone.blueprint.domain.gateway.PaymentSignaturePort;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.valueobject.PaymentChannel;
import com.bone.blueprint.domain.payment.valueobject.PaymentStatus;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.blueprint.infrastructure.persistence.AggregatePersistence;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandlePaymentCallbackCommandHandlerTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private TenantProvider tenantProvider;
  @Mock private PaymentSignaturePort paymentSignaturePort;
  @Spy private AggregatePersister aggregatePersister = new AggregatePersistence();
  @Mock private DomainEventPublisher domainEventPublisher;

  @InjectMocks private HandlePaymentCallbackCommandHandler handler;

  private Payment pendingPayment() {
    return Payment.create(
        1L, 1L, 100L, 200L, new BigDecimal("200"), PaymentChannel.SIMULATED, "http://pay");
  }

  @Test
  void testHandleSuccessConfirmPayment() {
    Payment payment = pendingPayment();
    // 保存时快照聚合已挂载的事件（AggregatePersistence 保存后会 clearDomainEvents，须在 save 时捕获）
    AtomicInteger eventCount = new AtomicInteger();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);
    when(paymentSignaturePort.verify(any(), any(), any())).thenReturn(true);
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(
            inv -> {
              eventCount.set(((Payment) inv.getArgument(0)).getDomainEvents().size());
              return 1L;
            });

    handler.handle(
        new HandlePaymentCallbackCommand(
            1L, "trade-001", new BigDecimal("200"), "valid-sign", true));

    assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    assertEquals("trade-001", payment.getChannelTradeNo());
    assertEquals(1, eventCount.get());
  }

  @Test
  void testHandleSuccessWithAmountMismatchThrows() {
    Payment payment = pendingPayment();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);
    when(paymentSignaturePort.verify(any(), any(), any())).thenReturn(true);

    assertThrows(
        DomainException.class,
        () ->
            handler.handle(
                new HandlePaymentCallbackCommand(
                    1L, "trade-001", new BigDecimal("199"), "valid-sign", true))); // 金额不符
    verify(paymentRepository, never()).save(any());
  }

  @Test
  void testHandleInvalidSignatureThrows() {
    Payment payment = pendingPayment();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);
    when(paymentSignaturePort.verify(any(), any(), any())).thenReturn(false);

    assertThrows(
        BizException.class,
        () ->
            handler.handle(
                new HandlePaymentCallbackCommand(
                    1L, "trade-001", new BigDecimal("200"), "forged-sign", true)));
    verify(paymentRepository, never()).save(any());
  }

  @Test
  void testHandleFailedMarksFailed() {
    Payment payment = pendingPayment();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);

    handler.handle(
        new HandlePaymentCallbackCommand(1L, "trade-002", new BigDecimal("200"), null, false));

    assertEquals(PaymentStatus.FAILED, payment.getStatus());
  }

  @Test
  void testHandleDuplicateCallbackSkips() {
    Payment payment = pendingPayment();
    payment.confirmSuccess("trade-001", new BigDecimal("200")); // 先成功一次
    payment.clearDomainEvents();

    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);
    when(paymentSignaturePort.verify(any(), any(), any())).thenReturn(true);

    // 重复成功回调：幂等跳过，不产生新事件
    handler.handle(
        new HandlePaymentCallbackCommand(
            1L, "trade-001", new BigDecimal("200"), "valid-sign", true));

    assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    // 幂等：聚合内 domainEvents 为空，保存时无新事件可发布
    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository, times(1)).save(captor.capture());
    assertEquals(0, captor.getValue().getDomainEvents().size());
  }

  @Test
  void testHandlePaymentNotFound() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(null);

    assertThrows(
        NotFoundException.class,
        () ->
            handler.handle(
                new HandlePaymentCallbackCommand(
                    1L, "trade-003", new BigDecimal("200"), null, true)));

    verify(paymentRepository, never()).save(any());
  }
}
