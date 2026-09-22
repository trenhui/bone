package com.bone.blueprint.application.event;

import com.bone.blueprint.application.event.support.OrderItemInventoryExecutor;
import com.bone.blueprint.application.event.support.OrderItemInventoryExecutor.StockActionFailure;
import com.bone.blueprint.application.event.support.StockActionFailureRecorder;
import com.bone.blueprint.application.integration.event.OrderStockActionFailedIntegrationEvent;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.model.order.event.OrderCreatedEvent;
import com.bone.blueprint.domain.repository.OrderRepository;
import java.time.Instant;
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
 * <p><b>失败处理</b>：单个商品预留失败不中断其余商品（避免局部失败放大为整单失败），但必须<strong>落 Outbox 可观测</strong>—— 经 {@link
 * StockActionFailureRecorder}（REQUIRES_NEW）写入 {@code OrderStockActionFailedIntegrationEvent}， 与
 * {@code OrderPaymentInconsistentIntegrationEvent} 共用告警/工单链路，否则库存与订单会静默不一致、直到超卖才暴露。
 *
 * <p><b>为何不加 {@code @Transactional}</b>：本处理器只读库 + 远程调用，无本地写入；开启事务只会让远程调用 期间白占数据库连接（失败上报由 {@link
 * StockActionFailureRecorder} 在独立短事务内完成）。
 *
 * <p>「明细为空显式留痕」「单笔失败不中断」「明细取自读侧投影而非重载聚合」三条规则及其实现，与 {@link OrderPaidEventHandler} 收敛在同一份骨架 {@link
 * OrderItemInventoryExecutor}，避免两条链路各自演进后规则漂移。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedEventHandler {

  private final OrderRepository orderRepository;
  private final InventoryGateway inventoryGateway;
  private final StockActionFailureRecorder stockFailureRecorder;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(OrderCreatedEvent event) {
    log.info("订单已创建: orderId={}, tenantId={}", event.orderId(), event.tenantId());

    OrderItemInventoryExecutor.forEachItem(
        orderRepository,
        event.tenantId(),
        event.orderId(),
        "预留",
        inventoryGateway::reserveStock,
        this::recordStockFailure);
  }

  private void recordStockFailure(StockActionFailure failure) {
    stockFailureRecorder.record(
        OrderStockActionFailedIntegrationEvent.fromDomain(
            failure.orderId(),
            failure.tenantId(),
            failure.productId(),
            failure.quantity(),
            failure.actionName(),
            failure.reason(),
            Instant.now()));
  }
}
