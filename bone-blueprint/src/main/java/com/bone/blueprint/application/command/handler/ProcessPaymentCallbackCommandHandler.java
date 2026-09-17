package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.ProcessPaymentCallbackCommand;
import com.bone.blueprint.application.port.out.OrderOutboxWriter;
import com.bone.blueprint.application.port.out.PaymentSignaturePort;
import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.capability.Capability;
import com.bone.core.domain.DomainEvent;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付渠道回调用例：先验签、再幂等确认支付成功/失败，保存并发布领域事件。
 *
 * <p><b>验签在此处（application 层）</b>：按 E-4.2 分层约束，adapter 层禁止直引技术端口（PaymentSignaturePort）。 把验签收口到
 * Handler，HTTP / RPC / MQ 任意入口调用都会自动验签——不会因为新增通道而漏验。
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
    name = "ProcessPaymentCallback",
    description = "处理支付渠道回调：验签 + 幂等确认支付成功/失败",
    inputSchema =
        "{\"paymentId\": \"long\", \"channelTradeNo\": \"string\", \"paidAmount\": \"decimal\", \"success\": \"boolean\", \"signature\": \"string\"}",
    outputSchema = "{}",
    idempotent = true,
    cost = 3,
    retryable = true,
    timeout = 30)
public class ProcessPaymentCallbackCommandHandler {

  private final PaymentRepository paymentRepository;
  private final TenantProvider tenantProvider;
  private final OrderOutboxWriter orderOutboxWriter;
  private final DomainEventPublisher domainEventPublisher;
  private final PaymentSignaturePort paymentSignaturePort;

  @Transactional
  public void handle(ProcessPaymentCallbackCommand command) {
    // 验签（所有入口统一在此，不会漏）
    boolean trusted =
        paymentSignaturePort.verify(
            command.paymentId(),
            command.channelTradeNo(),
            command.paidAmount(),
            command.signature());
    if (!trusted) {
      throw new BizException(401, BlueprintErrorCodes.PAYMENT_SIGNATURE_INVALID + ": 支付回调签名校验失败");
    }

    long tenantId = tenantProvider.currentTenantId();
    // 以支付单号定位支付单（真实渠道回调通常携带支付单号或渠道流水号）
    Payment payment =
        Optional.ofNullable(paymentRepository.findByIdInTenant(command.paymentId(), tenantId))
            .orElseThrow(() -> new NotFoundException("支付单不存在: paymentId=" + command.paymentId()));

    if (!command.success()) {
      try {
        payment.markFailed(command.channelTradeNo());
      } catch (com.bone.core.exception.DomainException ex) {
        // 状态冲突类 → 转 BizException + 错误码；金额/参数校验类让 DomainException 继续冒泡
        String msg = ex.getMessage();
        if (msg != null
            && (msg.contains("状态")
                || msg.contains("已成功")
                || msg.contains("已失败")
                || msg.contains("已关闭"))) {
          throw new BizException(409, BlueprintErrorCodes.PAYMENT_STATUS_CONFLICT + ": " + msg, ex);
        }
        throw ex;
      }
      paymentRepository.save(payment);
      domainEventPublisher.publishFrom(payment);
      return;
    }

    boolean migrated;
    try {
      migrated = payment.confirmSuccess(command.channelTradeNo(), command.paidAmount());
    } catch (com.bone.core.exception.DomainException ex) {
      String msg = ex.getMessage();
      if (msg != null
          && (msg.contains("状态")
              || msg.contains("已成功")
              || msg.contains("已失败")
              || msg.contains("已关闭"))) {
        throw new BizException(409, BlueprintErrorCodes.PAYMENT_STATUS_CONFLICT + ": " + msg, ex);
      }
      throw ex;
    }
    if (!migrated) {
      // 幂等跳过（同渠道流水号重复回调）：不写库、不发事件
      log.info(
          "支付回调幂等跳过: paymentId={}, channelTradeNo={}",
          command.paymentId(),
          command.channelTradeNo());
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
          command.paymentId(),
          command.channelTradeNo(),
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
