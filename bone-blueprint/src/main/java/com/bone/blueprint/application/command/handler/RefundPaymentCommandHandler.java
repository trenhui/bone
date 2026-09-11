package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.RefundPaymentCommand;
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
  private final DomainEventPublisher domainEventPublisher;

  @Transactional
  public void handle(RefundPaymentCommand command) {
    long tenantId = tenantProvider.currentTenantId();
    Payment payment =
        Optional.ofNullable(paymentRepository.findByIdInTenant(command.paymentId(), tenantId))
            .orElseThrow(() -> new NotFoundException("支付单不存在: paymentId=" + command.paymentId()));

    boolean refunded = payment.refund(command.refundAmount());

    paymentRepository.save(payment);
    domainEventPublisher.publishFrom(payment);
    if (refunded) {
      log.info("支付退款完成: paymentId={}, amount={}", command.paymentId(), command.refundAmount());
    } else {
      // 幂等跳过（金额完全一致的重复提交）。资金操作不可静默吞掉，留 warn 供对账与告警。
      log.warn(
          "支付退款重复提交已幂等跳过: paymentId={}, amount={}, 已退金额={}",
          command.paymentId(),
          command.refundAmount(),
          payment.getRefundedMoney().toBigDecimal());
    }
  }
}
