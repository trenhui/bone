package com.bone.blueprint.application.event;

import com.bone.blueprint.domain.payment.event.PaymentFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 支付失败记录（样板：仅日志；真实场景可触发订单关闭、通知用户等）。 */
@Slf4j
@Component
public class PaymentFailedEventHandler {

  @EventListener
  public void handle(PaymentFailedEvent event) {
    log.warn(
        "支付失败: paymentId={}, orderId={}, tenantId={}, amount={}",
        event.paymentId(),
        event.orderId(),
        event.tenantId(),
        event.amount());
  }
}
