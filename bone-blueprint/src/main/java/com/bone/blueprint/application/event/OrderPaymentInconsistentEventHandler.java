package com.bone.blueprint.application.event;

import com.bone.blueprint.application.event.outbox.OrderOutboxWriter;
import com.bone.blueprint.application.integration.event.OrderPaymentInconsistentIntegrationEvent;
import com.bone.blueprint.domain.order.event.OrderPaymentInconsistentEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 「钱货不一致」处理（AFTER_COMMIT）：落 Outbox 告警事件，使异常可观测、可补偿。
 *
 * <p><b>为何走 Outbox 而不是直接调告警通道</b>：告警/工单/IM 都是外部系统，可能超时或不可用；直接调用一旦 失败，异常就再次丢失，等于没补偿。落 Outbox
 * 由中继保证<strong>至少一次</strong>投递，与订单支付事件复用 同一套可靠机制——这是「异常也要可靠」的关键。
 *
 * <p><b>为何在 AFTER_COMMIT</b>：异常判定所在事务一旦回滚（例如订单并发被改），说明不一致的前提已消失， 不应产生告警噪声；提交后落库才代表「这个异常确实存在」。
 *
 * <p>下游消费方（对账 / 工单 / 自动退款）依据 {@code paymentId} 与 {@code orderStatus} 决定补偿动作， 例如对已取消却已收款的订单发起原路退款。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentInconsistentEventHandler {

  private final OrderOutboxWriter orderOutboxWriter;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional
  public void handle(OrderPaymentInconsistentEvent event) {
    log.error(
        "钱货不一致待补偿: orderId={}, tenantId={}, paymentId={}, orderStatus={}, reason={}",
        event.orderId(),
        event.tenantId(),
        event.paymentId(),
        event.orderStatus(),
        event.reason());

    orderOutboxWriter.appendPaymentInconsistent(
        OrderPaymentInconsistentIntegrationEvent.fromDomain(
            event.orderId(),
            event.tenantId(),
            event.paymentId(),
            event.orderStatus(),
            event.reason(),
            Instant.now()));
  }
}
