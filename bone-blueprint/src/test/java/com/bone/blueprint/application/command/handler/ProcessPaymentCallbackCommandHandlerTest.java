package com.bone.blueprint.application.command.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.command.cmd.ProcessPaymentCallbackCommand;
import com.bone.blueprint.application.port.out.OrderOutboxWriter;
import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.valueobject.PaymentChannel;
import com.bone.blueprint.domain.payment.valueobject.PaymentStatus;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.DomainException;
import com.bone.core.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * 支付回调用例测试。
 *
 * <p><b>不再断言 Handler 内验签</b>：验签已前移到 adapter（ADR-0022，覆盖成功/失败全分支），命令也不再携带 signature。
 *
 * <p><b>关键断言</b>：支付成功必须与 Outbox 写入<strong>同事务</strong>（P-5.4），且并发重复回调被唯一索引拦截时按幂等处理。
 */
@ExtendWith(MockitoExtension.class)
class ProcessPaymentCallbackCommandHandlerTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private TenantProvider tenantProvider;
  @Mock private OrderOutboxWriter orderOutboxWriter;
  @Mock private DomainEventPublisher domainEventPublisher;

  @InjectMocks private ProcessPaymentCallbackCommandHandler handler;

  private Payment pendingPayment() {
    return Payment.create(
        1L, 1L, 100L, 200L, new BigDecimal("200"), PaymentChannel.SIMULATED, "http://pay");
  }

  @Test
  void testHandleSuccessConfirmPaymentAndAppendOutbox() {
    Payment payment = pendingPayment();
    AtomicInteger eventCount = new AtomicInteger();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(
            inv -> {
              eventCount.set(((Payment) inv.getArgument(0)).getDomainEvents().size());
              return 1L;
            });

    handler.handle(new ProcessPaymentCallbackCommand(1L, "trade-001", new BigDecimal("200"), true));

    assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    assertEquals("trade-001", payment.getChannelTradeNo());
    assertEquals(1, eventCount.get());
    // 资金事实必须与业务写同事务落 Outbox（不能依赖 AFTER_COMMIT 内存事件）
    verify(orderOutboxWriter, times(1)).appendPaymentSucceeded(any());
  }

  @Test
  void testHandleSuccessWithAmountMismatchThrows() {
    Payment payment = pendingPayment();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);

    assertThrows(
        DomainException.class,
        () ->
            handler.handle(
                new ProcessPaymentCallbackCommand(1L, "trade-001", new BigDecimal("199"), true)));
    verify(paymentRepository, never()).save(any());
    verify(orderOutboxWriter, never()).appendPaymentSucceeded(any());
  }

  @Test
  void testHandleFailedMarksFailed() {
    Payment payment = pendingPayment();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);

    handler.handle(
        new ProcessPaymentCallbackCommand(1L, "trade-002", new BigDecimal("200"), false));

    assertEquals(PaymentStatus.FAILED, payment.getStatus());
    // 失败回调不产生「支付成功」事实，不应写 Outbox
    verify(orderOutboxWriter, never()).appendPaymentSucceeded(any());
  }

  @Test
  void testHandleDuplicateCallbackSkips() {
    Payment payment = pendingPayment();
    payment.confirmSuccess("trade-001", new BigDecimal("200")); // 先成功一次
    payment.clearDomainEvents();

    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);

    handler.handle(new ProcessPaymentCallbackCommand(1L, "trade-001", new BigDecimal("200"), true));

    assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    // 幂等跳过：不写库、不发事件、不重复落 Outbox
    verify(paymentRepository, never()).save(any());
    verify(orderOutboxWriter, never()).appendPaymentSucceeded(any());
  }

  @Test
  void testConcurrentDuplicateCallbackHandledAsIdempotent() {
    Payment payment = pendingPayment();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);
    // 并发重复回调：唯一索引在 UPDATE 时抛约束冲突
    when(paymentRepository.save(any(Payment.class)))
        .thenThrow(new DuplicateKeyException("uk_bp_payment_tenant_channel"));

    handler.handle(new ProcessPaymentCallbackCommand(1L, "trade-001", new BigDecimal("200"), true));

    // 按幂等处理：不向上抛 500、不写 Outbox、不发布事件
    verify(orderOutboxWriter, never()).appendPaymentSucceeded(any());
    verify(domainEventPublisher, never()).publishFrom(any(Payment.class));
  }

  @Test
  void testHandlePaymentNotFound() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(null);

    assertThrows(
        NotFoundException.class,
        () ->
            handler.handle(
                new ProcessPaymentCallbackCommand(1L, "trade-003", new BigDecimal("200"), true)));

    verify(paymentRepository, never()).save(any());
  }

  @Test
  void testOutboxReceivesThePaymentSucceededDomainEvent() {
    Payment payment = pendingPayment();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentRepository.findByIdInTenant(1L, 1L)).thenReturn(payment);

    handler.handle(new ProcessPaymentCallbackCommand(1L, "trade-004", new BigDecimal("200"), true));

    ArgumentCaptor<com.bone.blueprint.domain.payment.event.PaymentSucceededEvent> captor =
        ArgumentCaptor.forClass(
            com.bone.blueprint.domain.payment.event.PaymentSucceededEvent.class);
    verify(orderOutboxWriter).appendPaymentSucceeded(captor.capture());
    assertEquals(1L, captor.getValue().paymentId());
    assertEquals("trade-004", captor.getValue().channelTradeNo());
  }
}
