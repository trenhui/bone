package com.bone.blueprint.application.event;

import com.bone.blueprint.domain.payment.event.PaymentFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 支付失败记录（样板：仅日志；真实场景可触发订单关闭、通知用户等）。
 *
 * <p>与其余事件处理器统一 AFTER_COMMIT 事务边界：即使将来要触发下游动作（通知等），也应在业务事务提交后再执行。
 */
@Slf4j
@Component
public class PaymentFailedEventHandler {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(PaymentFailedEvent event) {
    log.warn(
        "支付失败: paymentId={}, orderId={}, tenantId={}, amount={}",
        event.paymentId(),
        event.orderId(),
        event.tenantId(),
        event.amount());
  }
}
