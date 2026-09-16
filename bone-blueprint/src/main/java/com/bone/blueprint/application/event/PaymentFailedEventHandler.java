package com.bone.blueprint.application.event;

import com.bone.blueprint.application.port.out.OrderOutboxWriter;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.event.PaymentFailedEvent;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 支付失败后续处理（AFTER_COMMIT + REQUIRES_NEW）。
 *
 * <p>支付失败不直接关联订单状态变更——订单仍处于 CREATED，用户可重新发起支付。但下游仍需感知： 通知用户、触发告警、写入对账记录。与订单退款/确认不同，这里没有跨聚合写，Outbox
 * 是唯一实质动作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailedEventHandler {

  private final PaymentRepository paymentRepository;
  private final OrderOutboxWriter orderOutboxWriter;
  private final DomainEventPublisher domainEventPublisher;

  /**
   * 支付单已由 {@code Payment.confirmSuccess} / {@code Payment.markFailed} 置终态并在独立事务提交后触发。
   *
   * <p>Outbox 在本 REQUIRES_NEW 内写入，与支付单写事务解耦（同 PaymentSucceededEventHandler 形态）。
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(PaymentFailedEvent event) {
    log.warn(
        "支付已失败，发布集成事件: paymentId={}, orderId={}, tenantId={}",
        event.paymentId(),
        event.orderId(),
        event.tenantId());

    // 幂等校验：支付单若已离开 FAILED（补偿恢复后重新成功），不应再发"失败"集成事件。
    Payment payment =
        Optional.ofNullable(paymentRepository.findByIdInTenant(event.paymentId(), event.tenantId()))
            .orElseThrow(() -> new NotFoundException("支付单不存在: " + event.paymentId()));
    if (!payment.isSuccess()) {
      // Outbox 原子落库——与支付单最终状态在同一事务内。
      orderOutboxWriter.appendPaymentFailed(event);
    } else {
      log.info("支付单状态已迁移为 SUCCESS，跳过失败集成事件: paymentId={}", event.paymentId());
    }
  }
}
