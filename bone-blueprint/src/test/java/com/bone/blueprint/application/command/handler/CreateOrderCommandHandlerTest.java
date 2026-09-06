package com.bone.blueprint.application.command.handler;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.OrderItemRepository;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateOrderCommandHandlerTest {

  @Mock private OrderRepository orderRepository;

  @Mock private OrderItemRepository orderItemRepository;

  @Mock private InventoryGateway inventoryGateway;

  @Mock private OrderPriceCalculator priceCalculator;

  @Mock private TenantProvider tenantProvider;
  @Mock private DomainEventPublisher domainEventPublisher;

  @InjectMocks private CreateOrderCommandHandler handler;

  private CreateOrderCommand command;

  @BeforeEach
  void setUp() {
    CreateOrderCommand.OrderItemDto itemDto =
        new CreateOrderCommand.OrderItemDto(1L, "商品1", 2, new BigDecimal("100"));

    command = new CreateOrderCommand(1L, Collections.singletonList(itemDto));
  }

  @Test
  void testHandleSuccess() {
    when(inventoryGateway.checkStock(anyLong(), anyInt())).thenReturn(true);
    when(priceCalculator.calculate(any())).thenReturn(new BigDecimal("200"));
    when(orderRepository.save(any(Order.class))).thenReturn(1L);

    Long orderId = handler.handle(command);

    assertNotNull(orderId);
    verify(inventoryGateway, times(1)).checkStock(1L, 2);
    // 回归防护：库存预留是远程写，已从下单事务移除（避免远程成功+本地回滚的「库存悬挂」），
    // 改由 OrderCreatedEvent 的 AFTER_COMMIT 订阅器异步执行
    verify(inventoryGateway, never()).reserveStock(anyLong(), anyLong(), anyInt());
    verify(priceCalculator, times(1)).calculate(any());
    verify(orderRepository, times(1)).save(any(Order.class));
    verify(orderItemRepository, times(1)).save(any(OrderItem.class));
    verify(domainEventPublisher, times(1)).publishFrom(any(Order.class));
  }

  @Test
  void testHandleInsufficientStock() {
    when(inventoryGateway.checkStock(anyLong(), anyInt())).thenReturn(false);

    assertThrows(BizException.class, () -> handler.handle(command));

    verify(orderRepository, never()).save(any(Order.class));
    verify(inventoryGateway, never()).reserveStock(anyLong(), anyLong(), anyInt());
  }

  @Test
  void testHandleMultipleItems() {
    CreateOrderCommand.OrderItemDto item1 =
        new CreateOrderCommand.OrderItemDto(1L, "商品1", 2, new BigDecimal("100"));

    CreateOrderCommand.OrderItemDto item2 =
        new CreateOrderCommand.OrderItemDto(2L, "商品2", 1, new BigDecimal("50"));

    CreateOrderCommand multiItemCommand = new CreateOrderCommand(1L, Arrays.asList(item1, item2));

    when(inventoryGateway.checkStock(anyLong(), anyInt())).thenReturn(true);
    when(priceCalculator.calculate(any())).thenReturn(new BigDecimal("250"));
    when(orderRepository.save(any(Order.class))).thenReturn(1L);

    Long orderId = handler.handle(multiItemCommand);

    assertNotNull(orderId);
    verify(inventoryGateway, times(2)).checkStock(anyLong(), anyInt());
    verify(inventoryGateway, never()).reserveStock(anyLong(), anyLong(), anyInt());
    verify(orderItemRepository, times(2)).save(any(OrderItem.class));
    verify(domainEventPublisher, times(1)).publishFrom(any(Order.class));
  }
}
