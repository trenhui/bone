package com.bone.blueprint.application.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.event.outbox.OrderOutboxWriter;
import com.bone.blueprint.domain.gateway.AggregatePersister;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.infrastructure.persistence.AggregatePersistence;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
  @Spy private AggregatePersister aggregatePersister = new AggregatePersistence();
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
}
