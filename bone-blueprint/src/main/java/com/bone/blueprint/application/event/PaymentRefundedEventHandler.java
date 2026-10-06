package com.bone.blueprint.application.event;

import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.model.order.projection.OrderWithItemsProjection;
import com.bone.blueprint.domain.model.payment.event.PaymentRefundedEvent;
import com.bone.blueprint.domain.model.shared.exception.OptimisticLockConflictException;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.tenant.context.TenantContextRunner;
import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import java.util.List;
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
      // 逐行释放（P1-1，2026-10-06）：与 reserve/confirm 的行粒度契约对齐。整单版
      // 释放走行粒度 releaseStockLine（见 InventoryGateway javadoc：接口已不暴露整单释放），
      // 事件驱动路径与另外两条库存链路保持同构，失败逐行留痕。
      try {
        // AFTER_COMMIT 线程无请求上下文（ADR-0031 D3 失败关闭）：读明细属于查询语义，但仍须显式声明租户，
        // 否则 SDK 查询会抛 MissingTenantContextException，被外层 catch 吞成「释放失败」，真实成因不可见。
        List<OrderWithItemsProjection> rows =
            TenantContextRunner.callAs(
                event.tenantId(), () -> orderRepository.findOrderWithItems(event.orderId()));
        boolean hasItem = rows.stream().anyMatch(row -> row.getItemId() != null);
        if (!hasItem) {
          log.error("订单商品项为空，退款库存释放无法执行（疑似明细未随订单落库，需人工对账）: orderId={}", event.orderId());
        }
        for (OrderWithItemsProjection row : rows) {
          if (row.getItemId() == null) {
            continue;
          }
          try {
            inventoryGateway.releaseStockLine(
                event.orderId(), row.getProductId(), row.getQuantity());
          } catch (Exception rowEx) {
            log.error(
                "退款后库存释放失败（行级），需补偿对账: orderId={}, productId={}, quantity={}",
                event.orderId(),
                row.getProductId(),
                row.getQuantity(),
                rowEx);
          }
        }
      } catch (Exception ex) {
        // 明细读取失败：不能让单条退款的后半段失败冒泡炸掉 AFTER_COMMIT 链，
        // 留痕交给对账补偿（与 2026-10-06 前整单版的失败语义一致，只是粒度更细）。
        log.error("退款后库存释放失败（明细读取失败），需补偿对账: orderId={}", event.orderId(), ex);
      }
    } else {
      log.warn("订单当前状态不可退款，跳过订单确认: orderId={}, status={}", order.getId(), order.getStatus());
    }

    if (!refunded) {
      log.info("退款幂等跳过: 订单已 REFUNDED 或不可退款: orderId={}", order.getId());
    }
  }
}
