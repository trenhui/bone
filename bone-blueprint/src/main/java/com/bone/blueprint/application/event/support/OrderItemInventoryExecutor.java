package com.bone.blueprint.application.event.support;

import com.bone.blueprint.application.query.port.OrderQueryPort;
import com.bone.blueprint.application.query.projection.OrderWithItemsProjection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * 订单明细 → 库存远程动作的公共执行骨架（供 {@link OrderCreatedEventHandler} / {@link OrderPaidEventHandler} 共用）。
 *
 * <p><b>为何收敛到一处</b>：两个处理器原先各有一份等长实现，差异只有「网关方法」与日志措辞，但它们必须遵守的是 <strong>同两条规则</strong>：
 *
 * <ol>
 *   <li><b>明细为空必须显式留痕</b>——静默跳过会让库存永不预留/扣减且毫无报错，问题潜伏至超卖才暴露；
 *   <li><b>单笔失败不中断其余明细</b>——局部失败不应放大为整单失败，但必须留痕待补偿对账。
 * </ol>
 *
 * 两份实现一旦各自演进，就会出现「一条链路静默失败、另一条正常」的形态；收敛后规则只有一处可改。
 *
 * <p><b>明细来源</b>：订单明细是 Order 聚合的子实体，但聚合重载不含级联（SDK 无级联），{@code Order.getItems()} 恒为空，故必须走查询侧端口
 * {@link OrderQueryPort#findOrderWithItems} 读取，切勿依赖重载后的聚合。
 *
 * <p><b>为何不加 {@code @Transactional}</b>：本类只读库 + 远程调用，无本地写入；开启事务只会让远程调用期间白占数据库连接。
 */
@Slf4j
public final class OrderItemInventoryExecutor {

  /** 一次库存动作：作用于「订单 + 明细行」上的商品与数量。 */
  @FunctionalInterface
  public interface StockAction {

    void apply(long orderId, Long productId, Integer quantity);
  }

  private OrderItemInventoryExecutor() {}

  /**
   * 读取订单明细并逐行执行库存动作。
   *
   * @param orderQueryPort 读侧端口（明细由 {@code t_order_item} 投影而来）
   * @param tenantId 事件所属租户
   * @param orderId 订单 ID
   * @param actionName 动作名，仅用于日志措辞（如「预留」「确认扣减」）
   * @param action 远程库存动作（{@code reserveStock} / {@code confirmStock}）
   */
  public static void forEachItem(
      OrderQueryPort orderQueryPort,
      long tenantId,
      long orderId,
      String actionName,
      StockAction action) {
    List<OrderWithItemsProjection> rows = orderQueryPort.findOrderWithItems(tenantId, orderId);

    // 明细为空（含 LEFT JOIN 无匹配行时 itemId 为 NULL）时显式留痕，避免库存静默不同步。
    // 订单必有商品项（Order.create 已强制校验），为空只可能是明细未随订单落库；静默跳过会让库存永不
    // 同步且毫无报错，问题潜伏至超卖才暴露。宁可报错，也不要「看起来正常运行却什么都没做」。
    boolean hasItem = rows.stream().anyMatch(row -> row.getItemId() != null);
    if (!hasItem) {
      log.error(
          "订单商品项为空，库存{}无法执行（疑似明细未随订单落库）: orderId={}, tenantId={}", actionName, orderId, tenantId);
      return;
    }

    for (OrderWithItemsProjection row : rows) {
      if (row.getItemId() == null) {
        continue;
      }
      try {
        action.apply(orderId, row.getProductId(), row.getQuantity());
      } catch (Exception ex) {
        log.error(
            "库存{}失败，需补偿对账: orderId={}, productId={}, quantity={}",
            actionName,
            orderId,
            row.getProductId(),
            row.getQuantity(),
            ex);
      }
    }
  }
}
