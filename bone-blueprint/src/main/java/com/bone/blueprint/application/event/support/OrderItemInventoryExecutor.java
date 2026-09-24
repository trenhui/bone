package com.bone.blueprint.application.event.support;

import com.bone.blueprint.domain.model.order.projection.OrderWithItemsProjection;
import com.bone.blueprint.domain.repository.OrderRepository;
import java.util.List;
import java.util.function.Consumer;
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
 * <p><b>明细来源</b>：订单明细是 Order 聚合的子实体，但聚合重载不含级联（SDK 无级联），{@code Order.getItems()} 恒为空，故必须走领域仓储 {@link
 * OrderRepository#findOrderWithItems} 读取（ADR-0030 合并后读模型同住 {@code OrderRepository}），切勿依赖重载后的聚合。
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
   * 库存动作失败的不可变事实（供调用方映射为集成事件并落 Outbox）。
   *
   * <p>执行器只负责「读明细 + 远程动作 + 收敛规则」，不感知 Outbox 契约；失败事实经本记录交回调用方， 由调用方决定如何上报（落 Outbox /
   * 告警）。这样执行器对两条库存链路（预留 / 扣减）保持通用，不被任一链路的细节污染。
   */
  public record StockActionFailure(
      long orderId,
      long tenantId,
      long productId,
      int quantity,
      String actionName,
      String reason) {}

  /**
   * 读取订单明细并逐行执行库存动作。
   *
   * @param orderRepository 读侧领域仓储（明细由 {@code t_order_item} 投影而来）
   * @param tenantId 事件所属租户
   * @param orderId 订单 ID
   * @param actionName 动作名，仅用于日志措辞（如「预留」「确认扣减」）
   * @param action 远程库存动作（{@code reserveStock} / {@code confirmStock}）
   * @param onFailure 单笔动作失败时的回调（交付失败事实，由调用方落 Outbox / 告警）；可为 {@code null}
   */
  public static void forEachItem(
      OrderRepository orderRepository,
      long tenantId,
      long orderId,
      String actionName,
      StockAction action,
      Consumer<StockActionFailure> onFailure) {
    List<OrderWithItemsProjection> rows = orderRepository.findOrderWithItems(orderId);

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
        if (onFailure != null) {
          // 回调本身必须隔离：失败事实落 Outbox 走 REQUIRES_NEW，若其提交失败抛异常，
          // 绝不能再向外传播——否则会中断剩余明细的库存动作，把"单笔失败"放大成"整单静默不同步"，
          // 且异常会冒泡进 AFTER_COMMIT 处理器，恰好是本次改造要消灭的失败模式。
          try {
            onFailure.accept(
                new StockActionFailure(
                    orderId,
                    tenantId,
                    row.getProductId(),
                    row.getQuantity(),
                    actionName,
                    ex.getMessage()));
          } catch (Exception recordEx) {
            log.error(
                "库存失败事实落 Outbox 失败（本条 error 留痕，不影响其余明细）: orderId={}, productId={}",
                orderId,
                row.getProductId(),
                recordEx);
          }
        }
      }
    }
  }
}
