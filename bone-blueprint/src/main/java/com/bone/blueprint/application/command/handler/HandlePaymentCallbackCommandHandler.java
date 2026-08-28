package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.HandlePaymentCallbackCommand;
import com.bone.blueprint.domain.gateway.AggregatePersister;
import com.bone.blueprint.domain.gateway.PaymentSignaturePort;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付渠道回调用例：按支付单号查找支付单，验签后幂等确认支付成功/失败，保存并发布领域事件。
 *
 * <p>安全：成功回调必须先验签（{@link PaymentSignaturePort}），签名不可信直接拒绝，防伪造回调。 幂等：{@link Payment#confirmSuccess}
 * 对已成功的支付单重复回调返回 false（跳过），渠道重复通知不会造成重复入账。
 */
@Capability(
    name = "HandlePaymentCallback",
    description = "处理支付渠道回调：验签后幂等确认支付成功/失败",
    inputSchema =
        "{\"paymentId\": \"long\", \"channelTradeNo\": \"string\", \"paidAmount\": \"decimal\", \"signature\": \"string\", \"success\": \"boolean\"}",
    outputSchema = "{}",
    idempotent = true,
    cost = 3,
    retryable = true,
    timeout = 30)
@Component
@RequiredArgsConstructor
public class HandlePaymentCallbackCommandHandler {

  private final PaymentRepository paymentRepository;
  private final TenantProvider tenantProvider;
  private final PaymentSignaturePort paymentSignaturePort;
  private final AggregatePersister aggregatePersister;
  private final DomainEventPublisher domainEventPublisher;

  @Transactional
  public void handle(HandlePaymentCallbackCommand cmd) {
    long tenantId = tenantProvider.currentTenantId();
    // 以支付单号定位支付单（真实渠道回调通常携带支付单号或渠道流水号）
    Payment payment =
        Optional.ofNullable(paymentRepository.findByIdInTenant(cmd.getPaymentId(), tenantId))
            .orElseThrow(() -> new NotFoundException("支付单不存在: paymentId=" + cmd.getPaymentId()));

    if (cmd.isSuccess()) {
      // 成功回调必须验签（防伪造）；验签失败拒绝确认
      boolean trusted =
          paymentSignaturePort.verify(payment, cmd.getPaidAmount(), cmd.getSignature());
      if (!trusted) {
        throw new BizException("支付回调签名校验失败");
      }
      payment.confirmSuccess(cmd.getChannelTradeNo(), cmd.getPaidAmount());
    } else {
      payment.markFailed(cmd.getChannelTradeNo());
    }

    aggregatePersister.updateAndPublishEvents(paymentRepository, domainEventPublisher, payment);
  }
}
