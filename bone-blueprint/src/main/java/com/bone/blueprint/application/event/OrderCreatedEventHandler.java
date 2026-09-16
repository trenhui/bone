package com.bone.blueprint.application.event;

import com.bone.blueprint.application.query.dto.OrderWithItemsRow;
import com.bone.blueprint.application.query.port.OrderReadPort;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.event.OrderCreatedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订单创建提交后异步预留库存（AFTER_COMMIT）。
 *
 * <p><b>为何不在下单事务内预留</b>：{@code reserveStock} 是远程<strong>写</strong>，置于本地 {@code @Transactional}
 * 内会产生「库存悬挂」——远程预留成功而本地事务回滚，预留就再无对应订单，库存被永久占用； 且远程慢会拖长事务、占用连接池。此处订单已提交，预留失败不会造成悬挂，由补偿/对账兜底 （与
 * {@code confirmStock}/{@code releaseStock} 的最终一致策略一致）。
 *
 * <p><b>失败处理</b>：单个商品预留失败不中断其余商品（避免局部失败放大为整单失败），但必须<strong>留痕</strong>——
 * 生产应落「预留失败待处理」记录或触发告警，否则库存与订单会静默不一致。
 *
 * <p><b>为何不加 {@code @Transactional}</b>：本处理器只读库 + 远程调用，无本地写入；开启事务只会让远程调用 期间白占数据库连接。
 *
 * <p><b>明细来源</b>：订单明细是 Order 聚合的子实体，随订单在同事务落库（{@code CreateOrderCommandHandler} 显式逐条 {@code
 * save}）。但订单聚合重载不含级联（SDK 无级联），{@code Order.getItems()} 恒为空，故此处必须走查询侧端口 {@link
 * OrderReadPort#findOrderWithItems}（联 {@code t_order_item} 投影）读取明细，切勿依赖重载后的聚合。
 * 明细为空时显式留痕，避免「看起来正常却什么都没做」的静默失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedEventHandler {

  private final OrderReadPort orderReadPort;
  private final InventoryGateway inventoryGateway;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(OrderCreatedEvent event) {
    log.info("订单已创建: orderId={}, tenantId={}", event.orderId(), event.tenantId());

    List<OrderWithItemsRow> rows =
        orderReadPort.findOrderWithItems(event.tenantId(), event.orderId());

    // 明细为空（含 LEFT JOIN 无匹配行时 itemId 为 NULL）时显式留痕，避免库存静默不预留。
    // 订单必有商品项（Order.create 已强制校验），为空只可能是明细未随订单落库；静默跳过会让库存永不预留且毫无报错，
    // 问题潜伏至超卖才暴露。宁可报错，也不要「看起来正常运行却什么都没做」。
    boolean hasItem = rows.stream().anyMatch(r -> r.getItemId() != null);
    if (!hasItem) {
      log.error(
          "订单商品项为空，库存预留无法执行（疑似明细未随订单落库）: orderId={}, tenantId={}",
          event.orderId(),
          event.tenantId());
      return;
    }

    for (OrderWithItemsRow row : rows) {
      if (row.getItemId() == null) {
        continue;
      }
      try {
        inventoryGateway.reserveStock(event.orderId(), row.getProductId(), row.getQuantity());
      } catch (Exception ex) {
        log.error(
            "库存预留失败，需补偿对账: orderId={}, productId={}, quantity={}",
            event.orderId(),
            row.getProductId(),
            row.getQuantity(),
            ex);
      }
    }
  }
}
