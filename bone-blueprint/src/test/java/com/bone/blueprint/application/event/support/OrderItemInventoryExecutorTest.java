package com.bone.blueprint.application.event.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.domain.model.order.projection.OrderWithItemsProjection;
import com.bone.blueprint.domain.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/** 收敛到 {@link OrderItemInventoryExecutor} 的两条规则：明细为空显式留痕、单笔失败不中断其余明细。 */
@ExtendWith(MockitoExtension.class)
class OrderItemInventoryExecutorTest {

  private final OrderRepository orderRepository = mock(OrderRepository.class);

  private static OrderWithItemsProjection row(Long itemId, Long productId, Integer quantity) {
    return new OrderWithItemsProjection(
        1L,
        1L,
        new BigDecimal("10.00"),
        "CREATED",
        LocalDateTime.now(),
        itemId,
        productId,
        "p",
        quantity,
        new BigDecimal("5.00"),
        new BigDecimal("10.00"));
  }

  @Test
  void invokesActionPerItemAndSkipsOnFailureWhenPresent() {
    when(orderRepository.findOrderWithItems(1L))
        .thenReturn(List.of(row(10L, 100L, 2), row(11L, 101L, 3)));
    OrderItemInventoryExecutor.StockAction action =
        mock(OrderItemInventoryExecutor.StockAction.class);
    Consumer<OrderItemInventoryExecutor.StockActionFailure> onFailure = mock(Consumer.class);

    OrderItemInventoryExecutor.forEachItem(orderRepository, 1L, 1L, "预留", action, onFailure);

    verify(action, times(2)).apply(anyLong(), any(), any());
    verify(onFailure, never()).accept(any());
  }

  /**
   * C-1 回归：单笔动作的失败回调（落 Outbox 走 REQUIRES_NEW）若自身抛异常，绝不能再向外传播——否则会中断剩余明细的库存动作，
   * 把"单笔失败"放大成"整单静默不同步"。此测试锁死：所有动作失败、且 onFailure 也失败，forEachItem 仍处理完每一行、且不向外抛。
   */
  @Test
  void callbackFailureDoesNotAbortLoopOrEscape() {
    when(orderRepository.findOrderWithItems(1L))
        .thenReturn(List.of(row(10L, 100L, 2), row(11L, 101L, 3)));
    OrderItemInventoryExecutor.StockAction action =
        mock(OrderItemInventoryExecutor.StockAction.class);
    doThrow(new RuntimeException("remote down")).when(action).apply(anyLong(), any(), any());
    Consumer<OrderItemInventoryExecutor.StockActionFailure> onFailure = mock(Consumer.class);
    doThrow(new RuntimeException("recorder down")).when(onFailure).accept(any());

    // 必须不抛异常：循环不被中断、异常不出 catch。
    OrderItemInventoryExecutor.forEachItem(orderRepository, 1L, 1L, "预留", action, onFailure);

    // 两行都被处理（循环未被中断），每行都走了失败回调。
    verify(action, times(2)).apply(anyLong(), any(), any());
    verify(onFailure, times(2)).accept(any());
  }
}
