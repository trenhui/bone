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
import com.bone.blueprint.domain.order.event.OrderCreatedEvent;
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
 * 订单创建后异步预留库存测试。
 *
 * <p><b>回归防护 1（库存悬挂）</b>：预留<strong>必须</strong>在 AFTER_COMMIT 执行。若挪回下单事务内，
 * 远程写会造成「库存悬挂」——远程预留成功而本地事务回滚，预留再无对应订单，库存被永久占用。
 *
 * <p><b>回归防护 2（静默失败）</b>：明细为空时必须显式留痕，禁止静默跳过。订单必有商品项（{@code Order.create}
 * 已强制校验），为空只可能是聚合明细未被级联加载；静默跳过会让库存永不预留且<strong>毫无 报错</strong>，问题潜伏至超卖才被发现。宁可报错，也不要「看起来正常运行却什么都没做」。
 */
@ExtendWith(MockitoExtension.class)
class OrderCreatedEventHandlerTest {

  @Mock private OrderRepository orderRepository;

  @Mock private InventoryGateway inventoryGateway;

  @InjectMocks private OrderCreatedEventHandler handler;

  @Test
  void testReservesStockForEachItem() {
    Order order =
        Order.create(
            1L,
            1L,
            1L,
            Collections.singletonList(
                OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"))));
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    handler.handle(new OrderCreatedEvent(1L, 1L, 1L, Instant.now()));

    verify(inventoryGateway, times(1)).reserveStock(1L, 1L, 2);
  }

  @Test
  void testEmptyItemsDoesNotSilentlyReserve() {
    // 模拟「聚合明细未级联加载」的异常状态（Order.create 的不变量使其无法真实构造）
    Order order = mock(Order.class);
    when(order.getItems()).thenReturn(Collections.emptyList());
    when(orderRepository.findByIdInTenant(1L, 1L)).thenReturn(order);

    handler.handle(new OrderCreatedEvent(1L, 1L, 1L, Instant.now()));

    // 走显式留痕分支：不调用网关，也不会假装成功
    verify(inventoryGateway, never()).reserveStock(anyLong(), anyLong(), anyInt());
  }
}
