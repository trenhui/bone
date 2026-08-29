package com.bone.blueprint.application.command.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.command.cmd.DeliverOrderCommand;
import com.bone.blueprint.domain.gateway.TenantProvider;
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

@ExtendWith(MockitoExtension.class)
class DeliverOrderCommandHandlerTest {

  @Mock private OrderRepository orderRepository;
  @Mock private TenantProvider tenantProvider;

  @InjectMocks private DeliverOrderCommandHandler handler;

  private Order shippedOrder() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.confirmPaid();
    order.ship();
    return order;
  }

  @Test
  void testDeliverFromShipped() {
    Order order = shippedOrder();
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    handler.handle(new DeliverOrderCommand(1L));

    assertEquals(OrderStatus.DELIVERED, order.getStatus());
    verify(orderRepository).save(order);
  }

  @Test
  void testDeliverFromPaidThrows() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.confirmPaid(); // 未发货直接确认送达
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    assertThrows(DomainException.class, () -> handler.handle(new DeliverOrderCommand(1L)));
    verify(orderRepository, never()).save(any());
  }

  @Test
  void testDeliverNotFound() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> handler.handle(new DeliverOrderCommand(1L)));
    verify(orderRepository, never()).save(any());
  }
}
