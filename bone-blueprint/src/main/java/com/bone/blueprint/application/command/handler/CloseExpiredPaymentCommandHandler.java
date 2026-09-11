package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CloseExpiredPaymentCommand;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 关闭超时未支付支付单用例：加载支付单 → 领域方法 {@code close()}（仅 PENDING/PAYING 可关闭）→ 保存。
 *
 * <p>{@code Payment.close()} 不发领域事件，故直接 {@code repository.save}（与 Ship/Deliver 同模式）。
 */
@Capability(
    name = "CloseExpiredPayment",
    description = "关闭超时未支付的支付单",
    inputSchema = "{\"paymentId\": \"long\"}",
    outputSchema = "{}",
    idempotent = true,
    cost = 1,
    retryable = true,
    timeout = 10)
@Slf4j
@Component
@RequiredArgsConstructor
public class CloseExpiredPaymentCommandHandler {

  private final PaymentRepository paymentRepository;
  private final TenantProvider tenantProvider;

  @Transactional
  public void handle(CloseExpiredPaymentCommand command) {
    // 定时任务为异步入口，必须显式携带租户；缺失时回落当前上下文（仅 HTTP 入口）
    long tenantId =
        command.tenantId() != null ? command.tenantId() : tenantProvider.currentTenantId();
    Payment payment =
        Optional.ofNullable(paymentRepository.findByIdInTenant(command.paymentId(), tenantId))
            .orElseThrow(() -> new NotFoundException("支付单不存在: paymentId=" + command.paymentId()));

    payment.close();
    paymentRepository.save(payment);
    log.info("已关闭超时支付单: paymentId={}, tenantId={}", command.paymentId(), tenantId);
  }
}
