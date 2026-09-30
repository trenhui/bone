package com.bone.blueprint.application.event;

import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.model.payment.event.PaymentRefundedEvent;
import com.bone.blueprint.domain.model.shared.exception.OptimisticLockConflictException;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.tenant.context.TenantContextRunner;
import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 退款成功后续处理（AFTER_COMMIT + REQUIRES_NEW）：
 *
 * <ol>
 *   <li>确认订单已置 REFUNDED（独立事务，两段式跨聚合解耦——同 {@link PaymentSucceededEventHandler} 形态）；
 *   <li>释放库存（远程调用，最终一致，失败不回滚订单退款）。
 * </ol>
 *
 * <p><b>为何本类不再落 Outbox</b>：退款 Outbox 已在 {@code PaymentApplicationService.refund()} 主事务内 与 Payment
 * 状态更新同事务写入（{@code update → appendPaymentRefunded → publishFrom}）， 本订阅器只负责跨聚合的 Order
 * 状态迁移与远程库存释放（这两件事不能放进 Payment 主事务）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundedEventHandler {

  private final OrderRepository orderRepository;
  private final InventoryGateway inventoryGateway;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(PaymentRefundedEvent event) {
    log.info(
        "退款成功后续处理: paymentId={}, orderId={}, tenantId={}, refundAmount={}",
        event.paymentId(),
        event.orderId(),
        event.tenantId(),
        event.refundAmount());

    Order order =
        TenantContextRunner.callAs(
            event.tenantId(),
            () ->
                Optional.ofNullable(orderRepository.findById(event.orderId()))
                    .orElseThrow(
                        () ->
                            BlueprintErrors.supplier(
                                    BlueprintErrorCodes.ORDER_NOT_FOUND, event.orderId())
                                .get()));

    // 确认订单退款（本地聚合写，独立事务）。
    boolean refunded = false;
    if (order.isRefundable()) {
      order.refund();
      try {
        orderRepository.update(order);
      } catch (OptimisticLockingFailureException ex) {
        throw new OptimisticLockConflictException("Order", order.getId(), order.getVersion());
      }
      refunded = true;

      // 释放库存（远程调用，最终一致）。仅当订单退款成功时才释放——
      // Payment 已退回但 Order 不可退款时，库存不能放（货还没退）。
      try {
        inventoryGateway.releaseStock(event.orderId());
      } catch (Exception ex) {
        log.error("退款后库存释放失败，需补偿对账: orderId={}", event.orderId(), ex);
      }
    } else {
      log.warn("订单当前状态不可退款，跳过订单确认: orderId={}, status={}", order.getId(), order.getStatus());
    }

    if (!refunded) {
      log.info("退款幂等跳过: 订单已 REFUNDED 或不可退款: orderId={}", order.getId());
    }
  }
}
