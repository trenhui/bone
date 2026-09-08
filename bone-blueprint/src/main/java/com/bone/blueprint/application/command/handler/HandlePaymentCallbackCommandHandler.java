package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.HandlePaymentCallbackCommand;
import com.bone.blueprint.domain.gateway.OrderOutboxWriter;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.capability.Capability;
import com.bone.core.domain.DomainEvent;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付渠道回调用例：按支付单号查找支付单，幂等确认支付成功/失败，保存并发布领域事件。
 *
 * <p><b>验签不在此处</b>：按支付样板（{@code ddd/samples/payment.md} §3）与 ADR-0022，验签是 adapter
 * 边界的防腐职责，且必须覆盖<strong>全部</strong>回调分支（成功 / 失败 / 关闭）。放在 Handler 内会导致：① 新增 RPC / MQ 入站通道时容易漏验签；②
 * 仅成功分支验签时，伪造的失败回调可把支付单打成 FAILED， 真实成功回调随后被聚合拒绝（"已失败/已关闭的支付单无法确认成功"）。
 *
 * <p><b>Outbox 与业务同事务（P-5.4）</b>：支付成功是不可容忍丢失的资金事实，必须与支付单状态变更在 <strong>同一事务</strong>内落
 * Outbox，再由中继投递。此前仅在 AFTER_COMMIT 监听器中写 Outbox， 支付事务提交后、监听器执行前的崩溃会造成「支付已成功、事件已消失、订单永不确认」且无迹可寻。
 *
 * <p><b>并发幂等（E-9.6.2）</b>：聚合内幂等只覆盖串行重复回调；并发重复回调由 {@code uk_bp_payment_tenant_channel(tenant_id,
 * channel_trade_no)} 唯一索引兜底，约束冲突在此按幂等处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Capability(
    name = "HandlePaymentCallback",
    description = "处理支付渠道回调：幂等确认支付成功/失败（验签在 adapter 边界完成）",
    inputSchema =
        "{\"paymentId\": \"long\", \"channelTradeNo\": \"string\", \"paidAmount\": \"decimal\", \"success\": \"boolean\"}",
    outputSchema = "{}",
    idempotent = true,
    cost = 3,
    retryable = true,
    timeout = 30)
public class HandlePaymentCallbackCommandHandler {

  private final PaymentRepository paymentRepository;
  private final TenantProvider tenantProvider;
  private final OrderOutboxWriter orderOutboxWriter;
  private final DomainEventPublisher domainEventPublisher;

  @Transactional
  public void handle(HandlePaymentCallbackCommand cmd) {
    long tenantId = tenantProvider.currentTenantId();
    // 以支付单号定位支付单（真实渠道回调通常携带支付单号或渠道流水号）
    Payment payment =
        Optional.ofNullable(paymentRepository.findByIdInTenant(cmd.paymentId(), tenantId))
            .orElseThrow(() -> new NotFoundException("支付单不存在: paymentId=" + cmd.paymentId()));

    if (!cmd.success()) {
      payment.markFailed(cmd.channelTradeNo());
      paymentRepository.save(payment);
      domainEventPublisher.publishFrom(payment);
      return;
    }

    boolean migrated = payment.confirmSuccess(cmd.channelTradeNo(), cmd.paidAmount());
    if (!migrated) {
      // 幂等跳过（同渠道流水号重复回调）：不写库、不发事件
      log.info("支付回调幂等跳过: paymentId={}, channelTradeNo={}", cmd.paymentId(), cmd.channelTradeNo());
      return;
    }

    // 事件须在 publishFrom 清空前取出（Outbox 落库与业务写同事务）
    PaymentSucceededEvent succeededEvent = extractEvent(payment, PaymentSucceededEvent.class);

    try {
      paymentRepository.save(payment);
    } catch (DuplicateKeyException ex) {
      // 并发重复回调：另一条回调已用同一 channel_trade_no 落库成功，唯一索引拦截本次写入。
      // 按幂等处理（不回滚整个事务、不向上抛 500）——资金不可重复入账，也不可让渠道收到错误后无限重试。
      log.warn(
          "支付回调并发重复已被唯一索引拦截，按幂等跳过: paymentId={}, channelTradeNo={}",
          cmd.paymentId(),
          cmd.channelTradeNo(),
          ex);
      return;
    }

    // 与业务写同事务：支付成功事实先落库，投递由 Outbox 中继保证（至少一次）
    orderOutboxWriter.appendPaymentSucceeded(succeededEvent);
    domainEventPublisher.publishFrom(payment);
  }

  /** 从聚合已注册的领域事件中提取指定类型（须在 {@code publishFrom} 清空前调用）。 */
  private static <T extends DomainEvent> T extractEvent(Payment payment, Class<T> type) {
    return payment.getDomainEvents().stream()
        .filter(type::isInstance)
        .map(type::cast)
        .findFirst()
        .orElse(null);
  }
}
