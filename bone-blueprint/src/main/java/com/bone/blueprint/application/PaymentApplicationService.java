package com.bone.blueprint.application;

import com.bone.blueprint.application.command.cmd.RefundPaymentCommand;
import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付用例边界（ApplicationService First）。承载单聚合、无远程调用、无两段式事务的简单用例。
 *
 * <p>发起支付（两段式远程调用 + 事务拆分）、支付回调（验签 + 幂等状态机）仍保留独立 CommandHandler。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

  private final PaymentRepository paymentRepository;
  private final TenantProvider tenantProvider;
  private final DomainEventPublisher domainEventPublisher;

  /**
   * 对已成功支付单发起退款。加载支付单 → 领域方法 {@code refund()}（幂等 + 金额校验）→ 保存发布事件。
   *
   * <p>退款后的订单状态由 {@code PaymentRefundedEvent} 的 AFTER_COMMIT 订阅确认（跨聚合协作）。
   */
  @Transactional
  public void refund(RefundPaymentCommand command) {
    long tenantId = tenantProvider.currentTenantId();
    Payment payment =
        Optional.ofNullable(paymentRepository.findByIdInTenant(command.paymentId(), tenantId))
            .orElseThrow(
                () ->
                    new BizException(
                        404, BlueprintErrorCodes.PAYMENT_NOT_FOUND + ": " + command.paymentId()));

    boolean refunded;
    try {
      refunded = payment.refund(command.refundAmount());
    } catch (com.bone.core.exception.DomainException ex) {
      throw new BizException(
          409, BlueprintErrorCodes.PAYMENT_STATUS_CONFLICT + ": " + ex.getMessage(), ex);
    }

    paymentRepository.saveWithVersionCheck(payment);
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
