package com.bone.blueprint.application.event;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.gateway.OrderReadPort;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.order.read.OrderWithItemsRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
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
 *
 * <p><b>明细来源</b>：聚合重载不含级联（SDK 无级联），{@code Order.getItems()} 恒为空，故本处理器与 {@code
 * OrderCreatedEventHandler} 一致走 {@link OrderReadPort#findOrderWithItems} 读取明细，不依赖重载后的聚合。
 */
@ExtendWith(MockitoExtension.class)
class OrderPaidEventHandlerTest {

  @Mock private OrderReadPort orderReadPort;

  @Mock private InventoryGateway inventoryGateway;

  @InjectMocks private OrderPaidEventHandler handler;

  private static OrderWithItemsRow row(Long itemId, Long productId, Integer quantity) {
    return new OrderWithItemsRow(
        null, null, null, null, null, itemId, productId, null, quantity, null, null);
  }

  @Test
  void testConfirmsStockForEachItem() {
    when(orderReadPort.findOrderWithItems(1L, 1L)).thenReturn(List.of(row(1L, 1L, 2)));

    handler.handle(new OrderPaidEvent(1L, 1L, 1L, new BigDecimal("200"), Instant.now()));

    verify(inventoryGateway, times(1)).confirmStock(1L, 1L, 2);
  }

  @Test
  void testEmptyItemsDoesNotSilentlyConfirm() {
    // 模拟「明细未随订单落库」的异常状态：读侧无明细行
    when(orderReadPort.findOrderWithItems(1L, 1L)).thenReturn(Collections.emptyList());

    handler.handle(new OrderPaidEvent(1L, 1L, 1L, new BigDecimal("200"), Instant.now()));

    verify(inventoryGateway, never()).confirmStock(anyLong(), anyLong(), anyInt());
  }
}
