package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.InitiatePaymentCommand;
import com.bone.blueprint.application.command.cmd.InitiatePaymentResult;
import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.domain.gateway.PaymentGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.valueobject.PaymentChannel;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.core.util.DistributedIdGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 订单发起支付用例：加载订单 → 校验可支付 → 创建支付单 → 提交支付渠道 → 返回支付链接。
 *
 * <p><b>两段式事务（R9 远程调用不占 DB 事务）</b>：
 *
 * <ol>
 *   <li><b>Tx1（短事务）</b>：校验订单并落一笔 PENDING 支付单（payUrl 暂空），提交释放 DB 事务；
 *   <li><b>事务外</b>：调用渠道预下单（远程 HTTP），期间不持有 DB 连接/锁；失败则补偿关闭支付单并抛错；
 *   <li><b>Tx2（短事务）</b>：回填支付链接、进入 PAYING，提交。
 * </ol>
 *
 * <p>若 Tx1 后进程崩溃，PENDING 孤儿单由 {@code CloseExpiredPaymentJob} 超时清理，不产生脏数据。订单仅用于
 * 校验与取金额，不改写；支付单是本用例的写聚合（聚合间仅以 ID 引用，§3.1）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Capability(
    name = "InitiatePayment",
    description = "订单发起支付，生成支付单并返回支付链接",
    inputSchema = "{\"orderId\": \"long\"}",
    outputSchema = "{\"paymentId\": \"long\", \"payUrl\": \"string\"}",
    idempotent = false,
    cost = 2,
    retryable = false,
    timeout = 30)
public class InitiatePaymentCommandHandler {

  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;
  private final TenantProvider tenantProvider;
  private final DomainEventPublisher domainEventPublisher;
  private final TransactionTemplate transactionTemplate;

  /** 下单支付默认渠道（样板模拟）。 */
  private static final PaymentChannel CHANNEL = PaymentChannel.SIMULATED;

  public InitiatePaymentResult handle(InitiatePaymentCommand command) {
    long tenantId = tenantProvider.currentTenantId();
    long paymentId = DistributedIdGenerator.generateLongId();

    // Tx1：校验订单 + 创建 PENDING 支付单，先提交释放 DB 事务，再做远程调用
    Payment pending =
        transactionTemplate.execute(
            (TransactionCallback<Payment>)
                status -> {
                  Order order =
                      Optional.ofNullable(
                              orderRepository.findByIdInTenant(command.orderId(), tenantId))
                          .orElseThrow(
                              () ->
                                  new BizException(
                                      404,
                                      BlueprintErrorCodes.ORDER_NOT_FOUND
                                          + ": "
                                          + command.orderId()));
                  // 用意图揭示的聚合查询方法，而非直接比较枚举（状态解释权归聚合，反贫血 §17）
                  if (!order.isAwaitingPayment()) {
                    throw new BizException(
                        409, BlueprintErrorCodes.ORDER_STATUS_CONFLICT + ": " + order.getStatus());
                  }
                  Payment payment =
                      Payment.create(
                          paymentId,
                          tenantId,
                          order.getId(),
                          order.getCustomerId(),
                          order.getTotalMoney().toBigDecimal(),
                          CHANNEL,
                          null);
                  paymentRepository.save(payment);
                  domainEventPublisher.publishFrom(payment);
                  return payment;
                });

    // 事务外：渠道预下单（远程调用），不占用 DB 连接/锁
    final String payUrl;
    try {
      payUrl =
          paymentGateway.preCreatePayment(
              paymentId, pending.getOrderId(), pending.getAmountMoney().toBigDecimal());
    } catch (RuntimeException e) {
      closePaymentAfterRemoteFailure(paymentId, tenantId);
      log.error("支付渠道预下单失败，已关闭支付单: paymentId={}", paymentId, e);
      throw new BizException(
          502, BlueprintErrorCodes.PAYMENT_CHANNEL_PREPAY_FAILED + ": " + e.getMessage(), e);
    }

    // Tx2：回填支付链接，支付单进入 PAYING
    transactionTemplate.executeWithoutResult(
        status -> {
          Payment payment = loadPayment(paymentId, tenantId);
          try {
            payment.submitToChannel(payUrl);
          } catch (com.bone.core.exception.DomainException ex) {
            throw new BizException(
                409, BlueprintErrorCodes.PAYMENT_STATUS_CONFLICT + ": " + ex.getMessage(), ex);
          }
          paymentRepository.saveWithVersionCheck(payment);
          domainEventPublisher.publishFrom(payment);
        });

    return new InitiatePaymentResult(paymentId, payUrl);
  }

  /** 远程预下单失败补偿：关闭孤儿支付单，避免 PENDING 悬空。 */
  private void closePaymentAfterRemoteFailure(long paymentId, long tenantId) {
    transactionTemplate.executeWithoutResult(
        status -> {
          Payment payment = loadPayment(paymentId, tenantId);
          payment.close();
          paymentRepository.saveWithVersionCheck(payment);
        });
  }

  private Payment loadPayment(long paymentId, long tenantId) {
    return Optional.ofNullable(paymentRepository.findByIdInTenant(paymentId, tenantId))
        .orElseThrow(
            () -> new BizException(404, BlueprintErrorCodes.PAYMENT_NOT_FOUND + ": " + paymentId));
  }
}
