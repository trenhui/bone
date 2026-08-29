package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.InitiatePaymentCommand;
import com.bone.blueprint.application.command.result.InitiatePaymentResult;
import com.bone.blueprint.domain.gateway.AggregatePersister;
import com.bone.blueprint.domain.gateway.PaymentGateway;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.valueobject.PaymentChannel;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import com.bone.core.util.DistributedIdGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单发起支付用例：加载订单 → 校验可支付 → 创建支付单 → 提交支付渠道 → 返回支付链接。
 *
 * <p>单事务内编排订单（读）与支付单（写）两个聚合：订单仅用于校验与取金额，不改写；支付单是本用例的 写聚合。跨聚合协作遵循「聚合间仅以 ID 引用」原则（§3.1）。
 */
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
  private final AggregatePersister aggregatePersister;
  private final DomainEventPublisher domainEventPublisher;

  /** 下单支付默认渠道（样板模拟）。 */
  private static final PaymentChannel CHANNEL = PaymentChannel.SIMULATED;

  @Transactional
  public InitiatePaymentResult handle(InitiatePaymentCommand cmd) {
    long tenantId = tenantProvider.currentTenantId();
    Order order =
        Optional.ofNullable(orderRepository.findByIdInTenant(cmd.orderId(), tenantId))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + cmd.orderId()));
    if (order.getStatus() != OrderStatus.CREATED) {
      throw new BizException("只有新建状态的订单可以发起支付: " + order.getStatus());
    }

    long paymentId = DistributedIdGenerator.generateLongId();
    // 先向渠道预下单，获取支付链接
    String payUrl =
        paymentGateway.preCreatePayment(
            paymentId, order.getId(), order.getTotalMoney().toBigDecimal());

    Payment payment =
        Payment.create(
            paymentId,
            tenantId,
            order.getId(),
            order.getCustomerId(),
            order.getTotalMoney().toBigDecimal(),
            CHANNEL,
            payUrl);
    payment.markPaying();

    aggregatePersister.saveAndPublishEvents(paymentRepository, domainEventPublisher, payment);

    return new InitiatePaymentResult(payment.getId(), payment.getPayUrl());
  }
}
