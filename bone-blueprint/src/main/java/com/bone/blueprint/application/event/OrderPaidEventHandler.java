package com.bone.blueprint.application.event;

import com.bone.blueprint.application.query.dto.OrderWithItemsRow;
import com.bone.blueprint.application.query.port.OrderReadPort;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订单支付成功后续处理（AFTER_COMMIT）：确认库存扣减。集成事件经 Outbox 异步中继。
 *
 * <p><b>明细来源</b>：与 {@link OrderCreatedEventHandler} 一致，订单聚合重载不含级联（SDK 无级联）， {@code
 * Order.getItems()} 恒为空，故必须走查询侧端口 {@link OrderReadPort#findOrderWithItems} 读取明细，
 * 切勿依赖重载后的聚合。明细为空时显式留痕，避免「看起来正常却什么都没做」的静默失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventHandler {

  private final OrderReadPort orderReadPort;
  private final InventoryGateway inventoryGateway;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(OrderPaidEvent event) {
    log.info("订单支付成功: orderId={}, tenantId={}", event.orderId(), event.tenantId());

    List<OrderWithItemsRow> rows =
        orderReadPort.findOrderWithItems(event.tenantId(), event.orderId());

    // 明细为空（含 LEFT JOIN 无匹配行时 itemId 为 NULL）时显式留痕，避免库存静默不扣减。
    // 订单必有商品项（Order.create 已强制校验），为空只可能是明细未随订单落库；静默跳过会让库存永不扣减且毫无报错，
    // 问题潜伏至超卖才暴露。宁可报错，也不要「看起来正常运行却什么都没做」。
    boolean hasItem = rows.stream().anyMatch(r -> r.getItemId() != null);
    if (!hasItem) {
      log.error(
          "订单商品项为空，库存扣减无法执行（疑似明细未随订单落库）: orderId={}, tenantId={}",
          event.orderId(),
          event.tenantId());
      return;
    }

    for (OrderWithItemsRow row : rows) {
      if (row.getItemId() == null) {
        continue;
      }
      try {
        inventoryGateway.confirmStock(event.orderId(), row.getProductId(), row.getQuantity());
      } catch (Exception ex) {
        log.error(
            "库存确认扣减失败，需补偿对账: orderId={}, productId={}, quantity={}",
            event.orderId(),
            row.getProductId(),
            row.getQuantity(),
            ex);
      }
    }
  }
}
