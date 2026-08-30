package com.bone.blueprint.application.event;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 订单支付后确认库存扣减测试。
 *
 * <p><b>回归防护 1（单一职责）</b>：库存确认<strong>只在本处理器</strong>执行。{@code PaymentSucceededEventHandler} 不得直接调
 * {@code confirmStock}，否则与 {@code Order.confirmPaid()} 发布的 {@code OrderPaidEvent} 链路重复，造成库存双重扣减。
 *
 * <p><b>回归防护 2（静默失败）</b>：明细为空时显式留痕。库存永不扣减却无任何报错是最危险的失败形态—— 表面上支付、发货一切正常，实际库存账实不符，直到盘点才暴露。
 */
@ExtendWith(MockitoExtension.class)
class OrderPaidEventHandlerTest {

  @Mock private OrderRepository orderRepository;

  @Mock private InventoryGateway inventoryGateway;

  @InjectMocks private OrderPaidEventHandler handler;

  @Test
  void testConfirmsStockForEachItem() {
    Order order =
        Order.create(
            1L,
            1L,
            1L,
            Collections.singletonList(
                OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"))));
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    handler.handle(new OrderPaidEvent(1L, 1L, 1L, new BigDecimal("200"), Instant.now()));

    verify(inventoryGateway, times(1)).confirmStock(1L, 1L, 2);
  }

  @Test
  void testEmptyItemsDoesNotSilentlyConfirm() {
    // 模拟「聚合明细未级联加载」的异常状态
    Order order = mock(Order.class);
    when(order.getItems()).thenReturn(Collections.emptyList());
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    handler.handle(new OrderPaidEvent(1L, 1L, 1L, new BigDecimal("200"), Instant.now()));

    verify(inventoryGateway, never()).confirmStock(anyLong(), anyLong(), anyInt());
  }
}
