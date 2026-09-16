package com.bone.blueprint.application.command.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.command.cmd.InitiatePaymentCommand;
import com.bone.blueprint.application.command.cmd.InitiatePaymentResult;
import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.domain.gateway.PaymentGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.valueobject.PaymentStatus;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InitiatePaymentCommandHandlerTest {

  @Mock private OrderRepository orderRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private PaymentGateway paymentGateway;
  @Mock private TenantProvider tenantProvider;
  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private TransactionTemplate transactionTemplate;

  @InjectMocks private InitiatePaymentCommandHandler handler;

  private InitiatePaymentCommand command;
  private Payment savedPayment;
  private final List<PaymentStatus> savedStatuses = new ArrayList<>();
  private final List<String> savedPayUrls = new ArrayList<>();

  /** 模拟 TransactionTemplate 直接执行回调（单测不引入 Spring 事务）。 */
  @BeforeEach
  void setUp() {
    command = new InitiatePaymentCommand(100L);
    when(transactionTemplate.execute(any()))
        .thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
    doAnswer(
            inv -> {
              ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());
    // save 记录最近一次保存的支付单（供 loadPayment 复用），并快照每次保存时的状态/链接
    doAnswer(
            inv -> {
              Payment p = inv.getArgument(0);
              savedPayment = p;
              savedStatuses.add(p.getStatus());
              savedPayUrls.add(p.getPayUrl());
              return null;
            })
        .when(paymentRepository)
        .save(any(Payment.class));
    when(paymentRepository.findByIdInTenant(anyLong(), anyLong())).thenAnswer(inv -> savedPayment);
  }

  private Order payableOrder() {
    OrderItem item = OrderItem.create(1L, 100L, 1L, "商品1", 2, new BigDecimal("100"));
    return Order.create(100L, 1L, 200L, Collections.singletonList(item));
  }

  @Test
  void testHandleSuccessReturnsPayUrl() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(100L, 1L)).thenReturn(payableOrder());
    when(paymentGateway.preCreatePayment(anyLong(), anyLong(), any()))
        .thenReturn("https://mock-pay.local/pay?order=100&token=abc");

    InitiatePaymentResult result = handler.handle(command);

    assertNotNull(result);
    assertNotNull(result.paymentId());
    assertTrue(result.payUrl().contains("order=100"));

    // 两段式：Tx1 落 PENDING（payUrl 空），远程成功后 Tx2 回填进入 PAYING
    verify(paymentRepository, times(2)).save(any(Payment.class));
    assertEquals(List.of(PaymentStatus.PENDING, PaymentStatus.PAYING), savedStatuses);
    assertEquals(
        Arrays.asList(null, "https://mock-pay.local/pay?order=100&token=abc"), savedPayUrls);
    assertEquals(100L, savedPayment.getOrderId());
    verify(paymentGateway, times(1)).preCreatePayment(anyLong(), anyLong(), any());
  }

  @Test
  void testHandleOrderNotFound() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(100L, 1L)).thenReturn(null);

    assertEquals(404, assertThrows(BizException.class, () -> handler.handle(command)).getCode());
    verify(paymentRepository, never()).save(any());
    verify(paymentGateway, never()).preCreatePayment(anyLong(), anyLong(), any());
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

  @Test
  void testHandleGatewayFailureClosesPayment() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(100L, 1L)).thenReturn(payableOrder());
    when(paymentGateway.preCreatePayment(anyLong(), anyLong(), any()))
        .thenThrow(new RuntimeException("mq down"));

    assertThrows(BizException.class, () -> handler.handle(command));

    // 远程失败补偿：Tx1 落 PENDING，补偿事务将其 CLOSED
    verify(paymentRepository, times(2)).save(any(Payment.class));
    assertEquals(List.of(PaymentStatus.PENDING, PaymentStatus.CLOSED), savedStatuses);
  }
}
