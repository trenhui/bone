package com.bone.blueprint.application.event;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.query.dto.OrderWithItemsProjection;
import com.bone.blueprint.application.query.port.OrderQueryPort;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.event.OrderCreatedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 订单创建后异步预留库存测试。
 *
 * <p><b>回归防护 1（库存悬挂）</b>：预留<strong>必须</strong>在 AFTER_COMMIT 执行。若挪回下单事务内，
 * 远程写会造成「库存悬挂」——远程预留成功而本地事务回滚，预留再无对应订单，库存被永久占用。
 *
 * <p><b>回归防护 2（静默失败）</b>：明细为空时必须显式留痕，禁止静默跳过。订单必有商品项（{@code Order.create} 已强制校验），
 * 为空只可能是明细未随订单落库；静默跳过会让库存永不预留且<strong>毫无报错</strong>，问题潜伏至超卖才被发现。 宁可报错，也不要「看起来正常运行却什么都没做」。
 *
 * <p>明细经查询侧 {@link OrderQueryPort#findOrderWithItems} 读取（订单聚合重载不含级联，{@code Order.getItems()} 恒为空）。
 */
@ExtendWith(MockitoExtension.class)
class OrderCreatedEventHandlerTest {

  @Mock private OrderQueryPort orderQueryPort;

  @Mock private InventoryGateway inventoryGateway;

  @InjectMocks private OrderCreatedEventHandler handler;

  @Test
  void testReservesStockForEachItem() {
    OrderWithItemsProjection row =
        new OrderWithItemsProjection(
            1L,
            1L,
            new BigDecimal("200"),
            "CREATED",
            LocalDateTime.now(),
            1L,
            1L,
            "商品1",
            2,
            new BigDecimal("100"),
            new BigDecimal("200"));
    when(orderQueryPort.findOrderWithItems(1L, 1L)).thenReturn(Collections.singletonList(row));

    handler.handle(new OrderCreatedEvent(1L, 1L, 1L, Instant.now()));

    verify(inventoryGateway, times(1)).reserveStock(1L, 1L, 2);
  }

  @Test
  void testEmptyItemsDoesNotSilentlyReserve() {
    // 模拟「明细未随订单落库」的异常状态（LEFT JOIN 无匹配行 itemId 为 NULL）
    OrderWithItemsProjection nullRow =
        new OrderWithItemsProjection(
            1L,
            1L,
            new BigDecimal("200"),
            "CREATED",
            LocalDateTime.now(),
            null,
            null,
            null,
            null,
            null,
            null);
    when(orderQueryPort.findOrderWithItems(1L, 1L)).thenReturn(List.of(nullRow));

    handler.handle(new OrderCreatedEvent(1L, 1L, 1L, Instant.now()));

    // 走显式留痕分支：不调用网关，也不会假装成功
    verify(inventoryGateway, never()).reserveStock(anyLong(), anyLong(), anyInt());
  }
}
