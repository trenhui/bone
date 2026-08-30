package com.bone.blueprint.application.event;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.event.OrderCreatedEvent;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedEventHandler {

  private final OrderRepository orderRepository;
  private final InventoryGateway inventoryGateway;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(OrderCreatedEvent event) {
    log.info("订单已创建: orderId={}, tenantId={}", event.orderId(), event.tenantId());

    Order order =
        Optional.ofNullable(orderRepository.findByIdInTenant(event.orderId(), event.tenantId()))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + event.orderId()));

    // 订单必有商品项（Order.create 已强制校验）。此处为空只可能是聚合内明细未被正确加载/持久化——
    // 若静默跳过，库存将永不预留且<strong>没有任何报错</strong>，问题会长期潜伏直至超卖。
    // 显式留痕让失败可见：宁可报错，也不要「看起来正常运行却什么都没做」。
    if (order.getItems().isEmpty()) {
      log.error(
          "订单商品项为空，库存预留无法执行（疑似聚合明细未级联加载）: orderId={}, tenantId={}",
          order.getId(),
          order.getTenantId());
      return;
    }

    for (OrderItem item : order.getItems()) {
      try {
        inventoryGateway.reserveStock(order.getId(), item.getProductId(), item.getQuantity());
      } catch (Exception ex) {
        log.error(
            "库存预留失败，需补偿对账: orderId={}, productId={}, quantity={}",
            order.getId(),
            item.getProductId(),
            item.getQuantity(),
            ex);
      }
    }
  }
}
