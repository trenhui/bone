package com.bone.blueprint.application.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.domain.gateway.OrderOutboxWriter;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.event.OrderPaymentInconsistentEvent;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付成功事件处理器测试。
 *
 * <p><b>回归防护</b>：本处理器**不直接调用** {@code inventoryGateway.confirmStock}——库存确认统一由 {@code
 * Order.confirmPaid()} 发布的 {@code OrderPaidEvent} 驱动 {@link OrderPaidEventHandler} 执行。若有人在本 处理器补回
 * confirmStock 调用，将造成库存双重扣减（见 PaymentSucceededEventHandler 类注释）。
 */
@ExtendWith(MockitoExtension.class)
class PaymentSucceededEventHandlerTest {

  @Mock private OrderRepository orderRepository;
  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private OrderOutboxWriter orderOutboxWriter;

  @InjectMocks private PaymentSucceededEventHandler handler;

  private Order pendingOrder() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    return Order.create(1L, 1L, 1L, Collections.singletonList(item));
  }

  private PaymentSucceededEvent event() {
    return new PaymentSucceededEvent(
        1L, 1L, 1L, new BigDecimal("200"), "trade-001", java.time.Instant.now());
  }

  @Test
  void testConfirmPaidWhenCreated() {
    Order order = pendingOrder();
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    handler.handle(event());

    // 订单确认为 PAID，且已持久化
    assertEquals(OrderStatus.PAID, order.getStatus());
    verify(orderRepository).save(order);
    // 同事务写 Outbox 集成事件（真实链路此前缺失，本次补齐）
    verify(orderOutboxWriter).appendOrderPaid(any());
  }

  @Test
  void testIdempotentWhenAlreadyPaid() {
    Order order = pendingOrder();
    order.confirmPaid(); // 已支付
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    handler.handle(event());

    // 幂等：已 PAID 不再重复确认、不重复保存、不重复写 Outbox
    assertEquals(OrderStatus.PAID, order.getStatus());
    verify(orderRepository, never()).save(any());
    verify(orderOutboxWriter, never()).appendOrderPaid(any());
  }

  @Test
  void testOrderNotFound() {
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> handler.handle(event()));
    verify(orderRepository, never()).save(any());
  }

  /**
   * 钱货不一致：支付单已 SUCCESS（钱已收），但订单已取消无法确认支付（货未付）。
   *
   * <p><b>回归防护</b>：异常<strong>不得静默吞掉</strong>。此前本分支仅 {@code log.warn} 后 return， 异常永久沉没、无人可感知。现必须发布
   * {@code OrderPaymentInconsistentEvent} 交由补偿链路落 Outbox。
   */
  @Test
  void testInconsistentStatePublishesCompensationEvent() {
    Order order = pendingOrder();
    order.cancel(); // CREATED → CANCELLED：回调到达时订单已取消
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    handler.handle(event());

    // 关键：绝不为了「让状态对上」而自动改单——那会掩盖真正的资金问题
    assertEquals(OrderStatus.CANCELLED, order.getStatus());
    verify(orderRepository, never()).save(any());
    // 订单并未支付，故不得写「订单已支付」Outbox
    verify(orderOutboxWriter, never()).appendOrderPaid(any());
    // 异常必须留痕：发布「钱货不一致」领域事件，且**同事务内**落 Outbox（v4.7 修正：禁止 AFTER_COMMIT
    // 另起事务写 Outbox，commit 后崩溃即丢事件）
    assertEquals(
        1,
        order.getDomainEvents().stream()
            .filter(e -> e instanceof OrderPaymentInconsistentEvent)
            .count());
    verify(domainEventPublisher).publishFrom(order);
    verify(orderOutboxWriter).appendPaymentInconsistent(any());
  }

  /** 幂等边界：已支付订单重复回调不算「不一致」，不产生补偿噪声。 */
  @Test
  void testPaidOrderDoesNotPublishCompensationEvent() {
    Order order = pendingOrder();
    order.confirmPaid();
    order.clearDomainEvents(); // 清掉 confirmPaid 产生的事件，便于断言本次无新增
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    handler.handle(event());

    assertEquals(0, order.getDomainEvents().size());
  }
}
