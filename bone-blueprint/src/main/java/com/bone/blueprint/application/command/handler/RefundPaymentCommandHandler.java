package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.RefundPaymentCommand;
import com.bone.blueprint.domain.gateway.AggregatePersister;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付退款用例：加载支付单 → 领域方法 {@code refund}（幂等 + 金额校验）→ 保存发布事件。
 *
 * <p>退款后的订单状态由 {@code PaymentRefundedEvent} 的 AFTER_COMMIT 订阅确认（跨聚合协作）。
 */
@Capability(
    name = "RefundPayment",
    description = "对已成功支付单发起退款（幂等 + 金额校验）",
    inputSchema = "{\"paymentId\": \"long\", \"refundAmount\": \"decimal\"}",
    outputSchema = "{}",
    idempotent = true,
    cost = 3,
    retryable = true,
    timeout = 30)
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundPaymentCommandHandler {

  private final PaymentRepository paymentRepository;
  private final TenantProvider tenantProvider;
  private final AggregatePersister aggregatePersister;
  private final DomainEventPublisher domainEventPublisher;

  @Transactional
  public void handle(RefundPaymentCommand cmd) {
    long tenantId = tenantProvider.currentTenantId();
    Payment payment =
        Optional.ofNullable(paymentRepository.findByIdInTenant(cmd.paymentId(), tenantId))
            .orElseThrow(() -> new NotFoundException("支付单不存在: paymentId=" + cmd.paymentId()));

    payment.refund(cmd.refundAmount());

    aggregatePersister.updateAndPublishEvents(paymentRepository, domainEventPublisher, payment);
    log.info("支付退款完成: paymentId={}, amount={}", cmd.paymentId(), cmd.refundAmount());
  }
}
