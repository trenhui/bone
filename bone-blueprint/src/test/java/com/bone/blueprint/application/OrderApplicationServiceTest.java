package com.bone.blueprint.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.command.cmd.DeliverOrderCommand;
import com.bone.blueprint.application.command.cmd.ShipOrderCommand;
import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.exception.DomainException;
import com.bone.core.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link OrderApplicationService} — ship / deliver 用例单元测试。 */
@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

  @Mock private OrderRepository orderRepository;
  @Mock private TenantProvider tenantProvider;

  @InjectMocks private OrderApplicationService service;

  // ===== ship() =====

  private Order paidOrder() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.confirmPaid();
    return order;
  }

  @Test
  void ship_fromPaid_success() {
    Order order = paidOrder();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    service.ship(new ShipOrderCommand(1L));

    assertEquals(OrderStatus.SHIPPED, order.getStatus());
    verify(orderRepository).save(order);
  }

  @Test
  void ship_fromCreated_throws() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    assertThrows(DomainException.class, () -> service.ship(new ShipOrderCommand(1L)));
    verify(orderRepository, never()).save(any());
  }

  @Test
  void ship_notFound_throws() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> service.ship(new ShipOrderCommand(1L)));
    verify(orderRepository, never()).save(any());
  }

  // ===== deliver() =====

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
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    service.deliver(new DeliverOrderCommand(1L));

    assertEquals(OrderStatus.DELIVERED, order.getStatus());
    verify(orderRepository).save(order);
  }

  @Test
  void deliver_fromPaid_throws() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.confirmPaid(); // 未发货直接确认送达
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    assertThrows(DomainException.class, () -> service.deliver(new DeliverOrderCommand(1L)));
    verify(orderRepository, never()).save(any());
  }

  @Test
  void deliver_notFound_throws() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> service.deliver(new DeliverOrderCommand(1L)));
    verify(orderRepository, never()).save(any());
  }
}
