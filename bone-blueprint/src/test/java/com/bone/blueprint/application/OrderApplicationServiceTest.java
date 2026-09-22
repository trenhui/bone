package com.bone.blueprint.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.command.CreateOrderCommand;
import com.bone.blueprint.application.command.DeliverOrderCommand;
import com.bone.blueprint.application.command.ShipOrderCommand;
import com.bone.blueprint.application.port.out.PricingPort;
import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.model.order.OrderItem;
import com.bone.blueprint.domain.model.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.model.shared.valueobject.Money;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link OrderApplicationService} 单元测试。
 *
 * <p>create() 的库存校验、定价编排、明细持久化场景来自原 {@code CreateOrderCommandHandlerTest}， 因 Handler 已内联到本 Service
 * 而迁移。
 */
@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

  // ========== 写侧 mock ==========
  @Mock private OrderRepository orderRepository;
  @Mock private InventoryGateway inventoryGateway;
  @Mock private PricingPort pricingService;
  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private TenantPort tenantProvider;

  @InjectMocks private OrderApplicationService service;

  // ===================== create() =====================

  @Test
  void create_success_checksStockPricingAndPersists() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(inventoryGateway.checkStock(anyLong(), anyInt())).thenReturn(true);
    when(pricingService.calculateFinalPrice(any(), eq(1L)))
        .thenReturn(Money.of(new BigDecimal("99.00")));

    CreateOrderCommand.OrderItemDto item =
        new CreateOrderCommand.OrderItemDto(7L, "样例商品", 2, new BigDecimal("50.00"));
    Long orderId = service.create(new CreateOrderCommand(1L, Collections.singletonList(item)));

    // 成功返回非空 id
    assertEquals(true, orderId != null);
    // 库存同步读校验（1 个商品调 1 次）
    verify(inventoryGateway, times(1)).checkStock(7L, 2);
    // 回归防护：库存预留是远程写，已从下单事务移除
    verify(inventoryGateway, never()).reserveStock(anyLong(), anyLong(), anyInt());
    // 定价扩展点被调用
    verify(pricingService, times(1)).calculateFinalPrice(any(), eq(1L));
    // 订单根 save（明细由 SDK @Cascade 随根落盘，应用层不再调 OrderItemRepository）
    verify(orderRepository, times(1)).save(any(Order.class));
    // 领域事件发布
    verify(domainEventPublisher, times(1)).publishFrom(any(Order.class));
  }

  @Test
  void create_insufficientStock_throwsAndDoesNotPersist() {
    org.mockito.Mockito.lenient()
        .when(inventoryGateway.checkStock(anyLong(), anyInt()))
        .thenReturn(false);

    CreateOrderCommand.OrderItemDto item =
        new CreateOrderCommand.OrderItemDto(7L, "缺货商品", 2, new BigDecimal("50.00"));

    assertThrows(
        BizException.class,
        () -> service.create(new CreateOrderCommand(1L, Collections.singletonList(item))));

    verify(orderRepository, never()).save(any());
    verify(inventoryGateway, never()).reserveStock(anyLong(), anyLong(), anyInt());
  }

  @Test
  void create_multipleItems_checksStockForEach() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(inventoryGateway.checkStock(anyLong(), anyInt())).thenReturn(true);
    when(pricingService.calculateFinalPrice(any(), eq(1L)))
        .thenReturn(Money.of(new BigDecimal("200.00")));

    CreateOrderCommand.OrderItemDto i1 =
        new CreateOrderCommand.OrderItemDto(1L, "商品A", 2, new BigDecimal("50.00"));
    CreateOrderCommand.OrderItemDto i2 =
        new CreateOrderCommand.OrderItemDto(2L, "商品B", 1, new BigDecimal("100.00"));
    Long orderId = service.create(new CreateOrderCommand(1L, List.of(i1, i2)));

    assertEquals(true, orderId != null);
    // 2 个商品各 check 1 次
    verify(inventoryGateway, times(2)).checkStock(anyLong(), anyInt());
    verify(orderRepository, times(1)).save(any(Order.class));
    verify(inventoryGateway, never()).reserveStock(anyLong(), anyLong(), anyInt());
  }

  // ===================== ship() =====================

  private Order paidOrder() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.confirmPaid();
    return order;
  }

  @Test
  void ship_fromPaid_success() {
    Order order = paidOrder();
    when(orderRepository.findById(1L)).thenReturn(order);

    service.ship(new ShipOrderCommand(1L));

    assertEquals(OrderStatus.SHIPPED, order.getStatus());
    verify(orderRepository).update(order);
  }

  @Test
  void ship_optimisticLockConflict_mapsTo409Not500() {
    Order order = paidOrder();
    when(orderRepository.findById(1L)).thenReturn(order);
    when(orderRepository.update(order))
        .thenThrow(new OptimisticLockingFailureException("Order", 1L, 0L));

    BizException ex =
        assertThrows(BizException.class, () -> service.ship(new ShipOrderCommand(1L)));

    // 并发冲突是「可重试的客户端冲突」，必须映射 409；否则会被兜底成 500 污染服务端告警/SLO。
    assertEquals(409, ex.getCode());
  }

  @Test
  void ship_fromCreated_throws() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    when(orderRepository.findById(1L)).thenReturn(order);

    assertThrows(BizException.class, () -> service.ship(new ShipOrderCommand(1L)));
    verify(orderRepository, never()).update(any());
  }

  @Test
  void ship_notFound_throws() {
    when(orderRepository.findById(1L)).thenReturn(null);

    assertEquals(
        404,
        assertThrows(BizException.class, () -> service.ship(new ShipOrderCommand(1L))).getCode());
    verify(orderRepository, never()).update(any());
  }

  // ===================== deliver() =====================

  private Order shippedOrder() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.confirmPaid();
    order.ship();
    return order;
  }

  @Test
  void deliver_fromShipped_success() {
    Order order = shippedOrder();
    when(orderRepository.findById(1L)).thenReturn(order);

    service.deliver(new DeliverOrderCommand(1L));

    assertEquals(OrderStatus.DELIVERED, order.getStatus());
    verify(orderRepository).update(order);
  }

  @Test
  void deliver_fromPaid_throws() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.confirmPaid(); // 未发货直接确认送达
    when(orderRepository.findById(1L)).thenReturn(order);

    assertThrows(BizException.class, () -> service.deliver(new DeliverOrderCommand(1L)));
    verify(orderRepository, never()).update(any());
  }

  @Test
  void deliver_notFound_throws() {
    when(orderRepository.findById(1L)).thenReturn(null);

    assertEquals(
        404,
        assertThrows(BizException.class, () -> service.deliver(new DeliverOrderCommand(1L)))
            .getCode());
    verify(orderRepository, never()).update(any());
  }
}
