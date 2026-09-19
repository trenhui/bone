package com.bone.blueprint.application.event;

import com.bone.blueprint.application.event.support.OrderItemInventoryExecutor;
import com.bone.blueprint.application.event.support.OrderItemInventoryExecutor.StockActionFailure;
import com.bone.blueprint.application.event.support.StockActionFailureRecorder;
import com.bone.blueprint.application.integration.event.OrderStockActionFailedIntegrationEvent;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.repository.OrderRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订单支付成功后续处理（AFTER_COMMIT）：确认库存扣减。
 *
 * <p><b>本类不写「订单已支付」Outbox</b>：{@code OrderPaidIntegrationEvent} 由 {@code
 * PaymentApplicationService.processCallback} 在<strong>回调事务内</strong>同事务落 Outbox（P-5.4）；本类只做
 * AFTER_COMMIT 的远程库存确认，两件事互不相干，此处也不重复投递。
 *
 * <p><b>但库存扣减失败会落 Outbox</b>：单笔 {@code confirmStock} 失败时经 {@link StockActionFailureRecorder}
 * （REQUIRES_NEW）写入 {@code OrderStockActionFailedIntegrationEvent}，与预留失败、钱货不一致共用告警/工单链路——
 * 否则「扣减静默失败」会与「预留正常」形成只单侧暴露的账实不符。
 *
 * <p>「明细为空显式留痕」「单笔失败不中断」「明细取自读侧投影而非重载聚合」三条规则及其实现，与 {@link OrderCreatedEventHandler} 收敛在同一份骨架 {@link
 * OrderItemInventoryExecutor}：两条链路在此处<strong>必须</strong>一致，否则会出现「预留静默失败、扣减正常」 这类只在单侧暴露的形态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventHandler {

  private final OrderRepository orderRepository;
  private final InventoryGateway inventoryGateway;
  private final StockActionFailureRecorder stockFailureRecorder;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(OrderPaidEvent event) {
    log.info("订单支付成功: orderId={}, tenantId={}", event.orderId(), event.tenantId());

    OrderItemInventoryExecutor.forEachItem(
        orderRepository,
        event.tenantId(),
        event.orderId(),
        "确认扣减",
        inventoryGateway::confirmStock,
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
