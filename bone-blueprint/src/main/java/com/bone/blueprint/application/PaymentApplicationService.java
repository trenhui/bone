package com.bone.blueprint.application;

import com.bone.blueprint.application.command.CloseExpiredPaymentCommand;
import com.bone.blueprint.application.command.InitiatePaymentCommand;
import com.bone.blueprint.application.command.InitiatePaymentResult;
import com.bone.blueprint.application.command.ProcessPaymentCallbackCommand;
import com.bone.blueprint.application.command.RefundPaymentCommand;
import com.bone.blueprint.application.port.out.OrderOutboxPort;
import com.bone.blueprint.application.port.out.PaymentSignaturePort;
import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.application.query.dto.PaymentDto;
import com.bone.blueprint.application.query.support.PaymentDetailAssembler;
import com.bone.blueprint.application.util.DomainEvents;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.gateway.PaymentGateway;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.event.PaymentFailedEvent;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.domain.payment.valueobject.PaymentChannel;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.blueprint.domain.shared.exception.OptimisticLockConflictException;
import com.bone.blueprint.domain.shared.exception.StateConflictException;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.DomainException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 支付应用层统一门面（Facade）——所有支付用例的唯一入口。
 *
 * <p><b>适配器只依赖这一个类</b>——Controller / RPC / JobHandler 不需要知道应用层内部实现细节。
 *
 * <p>内部按关注点选择实现方式：
 *
 * <ul>
 *   <li><b>复杂写</b>（initiate / processCallback）——代码量 ~80 行，涉及两段式事务、远程渠道调用、验签 + 幂等状态机，
 *       直接内联（OrderApplicationService.create() 已同模式内联 ~40 行，两段式 TransactionTemplate 是写法模式不值得单独拆类）。
 *   <li><b>简单写</b>（refund / closeExpired）——聚合加载 → 领域方法 → 保存。
 *   <li><b>读操作</b>（getById）——{@code PaymentDto} 是 {@code bp_payment}
 *       全行的子集，与写聚合<strong>无读模型分歧</strong>， 故直接走 {@link PaymentRepository} + {@link
 *       PaymentDetailAssembler}，不再绕一次读侧投影。 真正有分歧的读（全租户扫描）才走 {@link PaymentQueryPort}。
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

  // ========== 写侧依赖 ==========
  private final PaymentRepository paymentRepository;
  private final DomainEventPublisher domainEventPublisher;
  private final TenantPort tenantProvider;

  // initiate 专用
  private final OrderRepository orderRepository;
  private final PaymentGateway paymentGateway;
  private final TransactionTemplate transactionTemplate;

  // processCallback 专用
  private final PaymentSignaturePort paymentSignaturePort;
  private final OrderOutboxPort orderOutboxWriter;

  // ===================== 写操作 =====================

  /**
   * 订单发起支付——两段式事务（远程调用不占 DB 事务）。
   *
   * <ol>
   *   <li><b>Tx1（短事务）</b>：校验订单并落一笔 PENDING 支付单（payUrl 暂空），提交释放 DB 事务；
   *   <li><b>事务外</b>：调用渠道预下单（远程 HTTP），期间不持有 DB 连接/锁；失败则补偿关闭支付单并抛错；
   *   <li><b>Tx2（短事务）</b>：回填支付链接、进入 PAYING，提交。
   * </ol>
   *
   * <p>若 Tx1 后进程崩溃，PENDING 孤儿单由 {@code CloseExpiredPaymentJob} 超时清理，不产生脏数据。
   */
  public InitiatePaymentResult initiate(InitiatePaymentCommand command) {
    long tenantId = tenantProvider.currentTenantId();
    long paymentId = DistributedIdGenerator.generateLongId();

    // Tx1：校验订单 + 创建 PENDING 支付单，先提交释放 DB 事务，再做远程调用
    Payment pending =
        transactionTemplate.execute(
            (TransactionCallback<Payment>)
                status -> {
                  com.bone.blueprint.domain.order.Order order =
                      orderRepository
                          .findByIdInTenant(command.orderId(), tenantId)
                          .orElseThrow(
                              BlueprintErrors.supplier(
                                  BlueprintErrorCodes.ORDER_NOT_FOUND, command.orderId()));
                  if (!order.isAwaitingPayment()) {
                    throw BlueprintErrors.of(
                        BlueprintErrorCodes.ORDER_STATUS_CONFLICT, order.getStatus());
                  }
                  Payment payment =
                      Payment.create(
                          paymentId,
                          tenantId,
                          order.getId(),
                          order.getCustomerId(),
                          order.getTotalMoney().toBigDecimal(),
                          PaymentChannel.SIMULATED,
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
      throw BlueprintErrors.of(
          BlueprintErrorCodes.PAYMENT_CHANNEL_PREPAY_FAILED, e.getMessage(), e);
    }

    // Tx2：回填支付链接，支付单进入 PAYING
    transactionTemplate.executeWithoutResult(
        status -> {
          Payment payment = loadPayment(paymentId, tenantId);
          try {
            payment.submitToChannel(payUrl);
          } catch (DomainException ex) {
            throw BlueprintErrors.of(
                BlueprintErrorCodes.PAYMENT_STATUS_CONFLICT, ex.getMessage(), ex);
          }
          try {
            paymentRepository.update(payment);
          } catch (OptimisticLockingFailureException ex) {
            throw new OptimisticLockConflictException(
                "Payment", payment.getId(), payment.getVersion());
          }
          domainEventPublisher.publishFrom(payment);
        });

    return new InitiatePaymentResult(paymentId, payUrl);
  }

  /**
   * 支付渠道回调——验签 + 幂等状态机 + 并发拦截。
   *
   * <p><b>验签在此处（application 层）</b>：HTTP / RPC / MQ 任意入口调用都会自动验签，不会漏。
   *
   * <p><b>Outbox 与业务同事务</b>：支付成功是不可容忍丢失的资金事实，必须与支付单状态变更在同一事务内落 Outbox。
   *
   * <p><b>并发幂等</b>：聚合内幂等只覆盖串行重复回调；并发重复由唯一索引 + 乐观锁拦截，均按幂等处理（不回滚、不上抛 500）。
   */
  @Transactional
  public void processCallback(ProcessPaymentCallbackCommand command) {
    // 验签（所有入口统一在此，不会漏）
    boolean trusted =
        paymentSignaturePort.verify(
            command.paymentId(),
            command.channelTradeNo(),
            command.paidAmount(),
            command.signature());
    if (!trusted) {
      throw BlueprintErrors.of(BlueprintErrorCodes.PAYMENT_SIGNATURE_INVALID, "支付回调签名校验失败");
    }

    long tenantId = tenantProvider.currentTenantId();
    Payment payment =
        paymentRepository
            .findByIdInTenant(command.paymentId(), tenantId)
            .orElseThrow(
                BlueprintErrors.supplier(
                    BlueprintErrorCodes.PAYMENT_NOT_FOUND, command.paymentId()));

    if (!command.success()) {
      try {
        payment.markFailed(command.channelTradeNo());
      } catch (StateConflictException ex) {
        throw BlueprintErrors.of(BlueprintErrorCodes.PAYMENT_STATUS_CONFLICT, ex.getMessage(), ex);
      }
      // 事件须在 publishFrom 清空前取出
      PaymentFailedEvent failedEvent =
          DomainEvents.extract(payment.getDomainEvents(), PaymentFailedEvent.class);

      try {
        paymentRepository.update(payment);
      } catch (OptimisticLockingFailureException ex) {
        throw new OptimisticLockConflictException("Payment", payment.getId(), payment.getVersion());
      }
      // 与业务写同事务落 Outbox（与成功路径同形态）：若挪到 AFTER_COMMIT 再写，支付单已提交而事件未落库，
      // 其间崩溃即永久丢失——支付单是终态，没有补偿扫描覆盖它，失败事实会静默消失。
      orderOutboxWriter.appendPaymentFailed(failedEvent);
      domainEventPublisher.publishFrom(payment);
      return;
    }

    boolean migrated;
    try {
      migrated = payment.confirmSuccess(command.channelTradeNo(), command.paidAmount());
    } catch (StateConflictException ex) {
      throw BlueprintErrors.of(BlueprintErrorCodes.PAYMENT_STATUS_CONFLICT, ex.getMessage(), ex);
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
    PaymentSucceededEvent succeededEvent =
        DomainEvents.extract(payment.getDomainEvents(), PaymentSucceededEvent.class);

    try {
      paymentRepository.update(payment);
    } catch (OptimisticLockingFailureException | DuplicateKeyException ex) {
      // SDK 原生乐观锁冲突（ADR-0031 D2）/ 唯一索引冲突：并发重复回调按幂等处理（资金不可重复入账）。
      // 原 saveWithVersionCheck 抛出的 OptimisticLockConflictException 现由 SDK 的
      // OptimisticLockingFailureException 替代，二者语义一致——均在此按幂等跳过，保持 catch 行为有效。
      log.warn(
          "支付回调并发已拦截，按幂等跳过: paymentId={}, channelTradeNo={}, reason={}",
          command.paymentId(),
          command.channelTradeNo(),
          ex.getClass().getSimpleName());
      return;
    }

    // 与业务写同事务：支付成功事实先落库，投递由 Outbox 中继保证（至少一次）
    orderOutboxWriter.appendPaymentSucceeded(succeededEvent);
    domainEventPublisher.publishFrom(payment);
  }

  /** 对已成功支付单发起退款。加载支付单 → 领域方法 {@code refund()}（幂等 + 金额校验）→ 保存发布事件。 */
  @Transactional
  public void refund(RefundPaymentCommand command) {
    long tenantId = tenantProvider.currentTenantId();
    Payment payment =
        paymentRepository
            .findByIdInTenant(command.paymentId(), tenantId)
            .orElseThrow(
                BlueprintErrors.supplier(
                    BlueprintErrorCodes.PAYMENT_NOT_FOUND, command.paymentId()));

    boolean refunded;
    try {
      refunded = payment.refund(command.refundAmount());
    } catch (DomainException ex) {
      throw BlueprintErrors.of(BlueprintErrorCodes.PAYMENT_STATUS_CONFLICT, ex.getMessage(), ex);
    }

    try {
      paymentRepository.update(payment);
    } catch (OptimisticLockingFailureException ex) {
      throw new OptimisticLockConflictException("Payment", payment.getId(), payment.getVersion());
    }
    domainEventPublisher.publishFrom(payment);
    if (refunded) {
      log.info("支付退款完成: paymentId={}, amount={}", command.paymentId(), command.refundAmount());
    } else {
      log.warn(
          "支付退款重复提交已幂等跳过: paymentId={}, amount={}, 已退金额={}",
          command.paymentId(),
          command.refundAmount(),
          payment.getRefundedMoney().toBigDecimal());
    }
  }

  /**
   * 关闭超时未支付支付单。
   *
   * <p>定时任务为异步入口，必须显式携带租户；缺失时回落当前上下文（仅 HTTP 入口）。 {@code Payment.close()} 终态不发领域事件，故不调 {@code
   * publishFrom}。
   */
  @Transactional
  public void closeExpired(CloseExpiredPaymentCommand command) {
    long tenantId =
        command.tenantId() != null ? command.tenantId() : tenantProvider.currentTenantId();
    Payment payment =
        paymentRepository
            .findByIdInTenant(command.paymentId(), tenantId)
            .orElseThrow(
                BlueprintErrors.supplier(
                    BlueprintErrorCodes.PAYMENT_NOT_FOUND, command.paymentId()));

    try {
      payment.close();
    } catch (DomainException ex) {
      throw BlueprintErrors.of(BlueprintErrorCodes.PAYMENT_STATUS_CONFLICT, ex.getMessage(), ex);
    }
    try {
      paymentRepository.update(payment);
    } catch (OptimisticLockingFailureException ex) {
      throw new OptimisticLockConflictException("Payment", payment.getId(), payment.getVersion());
    }
    log.info("已关闭超时支付单: paymentId={}, tenantId={}", command.paymentId(), tenantId);
  }

  // ===================== 读操作 =====================

  /** 查询支付单详情（与写路径共用 {@link #loadPayment}，租户隔离语义一致）。 */
  @Transactional(readOnly = true)
  public PaymentDto getById(long paymentId) {
    long tenantId = tenantProvider.currentTenantId();
    return PaymentDetailAssembler.from(loadPayment(paymentId, tenantId));
  }

  // ===================== 私有辅助 =====================

  private void closePaymentAfterRemoteFailure(long paymentId, long tenantId) {
    transactionTemplate.executeWithoutResult(
        status -> {
          Payment payment = loadPayment(paymentId, tenantId);
          payment.close();
          try {
            paymentRepository.update(payment);
          } catch (OptimisticLockingFailureException ex) {
            throw new OptimisticLockConflictException(
                "Payment", payment.getId(), payment.getVersion());
          }
        });
  }

  private Payment loadPayment(long paymentId, long tenantId) {
    return paymentRepository
        .findByIdInTenant(paymentId, tenantId)
        .orElseThrow(BlueprintErrors.supplier(BlueprintErrorCodes.PAYMENT_NOT_FOUND, paymentId));
  }
}
