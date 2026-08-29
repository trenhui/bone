package com.bone.blueprint.application.command.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.command.cmd.InitiatePaymentCommand;
import com.bone.blueprint.application.command.result.InitiatePaymentResult;
import com.bone.blueprint.domain.gateway.AggregatePersister;
import com.bone.blueprint.domain.gateway.PaymentGateway;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.valueobject.PaymentStatus;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.blueprint.infrastructure.persistence.AggregatePersistence;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InitiatePaymentCommandHandlerTest {

  @Mock private OrderRepository orderRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private PaymentGateway paymentGateway;
  @Mock private TenantProvider tenantProvider;
  @Spy private AggregatePersister aggregatePersister = new AggregatePersistence();
  @Mock private DomainEventPublisher domainEventPublisher;

  @InjectMocks private InitiatePaymentCommandHandler handler;

  private InitiatePaymentCommand command;

  @BeforeEach
  void setUp() {
    command = new InitiatePaymentCommand(100L);
  }

  private Order payableOrder() {
    OrderItem item = OrderItem.create(1L, 100L, 1L, "商品1", 2, new BigDecimal("100"));
    return Order.create(100L, 1L, 200L, Collections.singletonList(item));
  }

  @Test
  void testHandleSuccessReturnsPayUrl() {
    Order order = payableOrder();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(100L, 1L)).thenReturn(order);
    when(paymentGateway.preCreatePayment(anyLong(), anyLong(), any()))
        .thenReturn("https://mock-pay.local/pay?order=100&token=abc");

    InitiatePaymentResult result = handler.handle(command);

    assertNotNull(result);
    assertNotNull(result.paymentId());
    assertNotNull(result.payUrl());
    assertTrue(result.payUrl().contains("order=100"));

    ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository, times(1)).save(paymentCaptor.capture());
    assertEquals(PaymentStatus.PAYING, paymentCaptor.getValue().getStatus());
    assertEquals(100L, paymentCaptor.getValue().getOrderId());
    verify(paymentGateway, times(1)).preCreatePayment(anyLong(), anyLong(), any());
  }

  @Test
  void testHandleOrderNotFound() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(100L, 1L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> handler.handle(command));
    verify(paymentRepository, never()).save(any());
  }

  @Test
  void testHandleNonCreatedOrderRejected() {
    Order order = payableOrder();
    order.confirmPaid(); // PAID 状态不可发起支付
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(100L, 1L)).thenReturn(order);

    assertThrows(BizException.class, () -> handler.handle(command));
    verify(paymentRepository, never()).save(any());
    verify(paymentGateway, never()).preCreatePayment(anyLong(), anyLong(), any());
  }
}
