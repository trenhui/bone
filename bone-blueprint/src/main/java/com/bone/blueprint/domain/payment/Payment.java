package com.bone.blueprint.domain.payment;

import com.bone.blueprint.domain.payment.event.PaymentFailedEvent;
import com.bone.blueprint.domain.payment.event.PaymentRefundedEvent;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.domain.payment.valueobject.PaymentChannel;
import com.bone.blueprint.domain.payment.valueobject.PaymentStatus;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 支付单聚合根（支付限界上下文）。
 *
 * <p>独立于订单聚合，通过 {@code orderId} 关联订单，遵循「聚合间仅以 ID 引用」原则（§3.1）。支付 成功经 {@link #confirmSuccess(String)}
 * 幂等确认，避免渠道重复回调造成重复入账。
 *
 * <p>状态机：{@link PaymentStatus#PENDING} → {@link PaymentStatus#PAYING} → {@link
 * PaymentStatus#SUCCESS} / {@link PaymentStatus#FAILED} / {@link PaymentStatus#CLOSED}。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bp_payment")
public class Payment extends TenantAggregateRoot<Long> {

  private Long orderId;
  private Long customerId;
  private BigDecimal amount;
  private PaymentChannel channel;
  private PaymentStatus status;

  /** 外部支付渠道流水号（渠道回填，幂等去重键）。 */
  private String channelTradeNo;

  /** 支付链接（渠道预下单返回，用户据此完成支付）。 */
  private String payUrl;

  private Instant paidAt;
  private Instant refundedAt;
  private BigDecimal refundAmount;
  private Instant createdAt;
  private Instant updatedAt;

  private Payment(
      Long id,
      Long tenantId,
      Long orderId,
      Long customerId,
      BigDecimal amount,
      PaymentChannel channel,
      String payUrl) {
    this.setId(id);
    this.setTenantId(tenantId);
    this.orderId = orderId;
    this.customerId = customerId;
    this.amount = amount;
    this.channel = channel;
    this.status = PaymentStatus.PENDING;
    this.payUrl = payUrl;
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  /**
   * 由订单发起支付：创建一笔处于 PENDING 的支付单。
   *
   * <p>金额由订单总额决定；入参 {@code amount} 须与订单一致，调用方（Handler）负责从订单读取。
   */
  public static Payment create(
      Long id,
      Long tenantId,
      Long orderId,
      Long customerId,
      BigDecimal amount,
      PaymentChannel channel,
      String payUrl) {
    if (id == null || orderId == null || customerId == null) {
      throw new DomainException("支付单必填字段缺失");
    }
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new DomainException("支付金额必须大于0");
    }
    return new Payment(id, tenantId, orderId, customerId, amount, channel, payUrl);
  }

  /** 已向支付渠道预下单：进入支付中，等待用户完成支付。 */
  public void markPaying() {
    if (this.status != PaymentStatus.PENDING) {
      throw new DomainException("只有新建状态的支付单可以提交支付");
    }
    this.status = PaymentStatus.PAYING;
    this.updatedAt = Instant.now();
  }

  /**
   * 渠道回调确认支付成功（幂等 + 金额一致性校验）。
   *
   * <p>幂等：已在 {@link PaymentStatus#SUCCESS} 的支付单再次回调，若 {@code channelTradeNo} 相同则直接
   * 返回（跳过、不重复发事件）；若不同视为异常渠道状态，抛错（防止渠道流水号被篡改覆盖）。PENDING/PAYING 可成功；FAILED/CLOSED 不能再成功。
   *
   * <p>金额一致性：实付金额 {@code paidAmount} 必须与应付金额 {@link #amount} 相等（业界支付核心不变量）， 防止部分支付/金额被篡改的异常入账。
   *
   * @param channelTradeNo 渠道流水号（幂等去重键）
   * @param paidAmount 渠道实付金额，须与应付金额一致
   * @return 本次调用是否真正完成状态迁移（false 表示幂等跳过）
   */
  public boolean confirmSuccess(String channelTradeNo, BigDecimal paidAmount) {
    if (this.status == PaymentStatus.SUCCESS) {
      if (!Objects.equals(this.channelTradeNo, channelTradeNo)) {
        throw new DomainException("支付单已成功，但回调渠道流水号不一致");
      }
      return false; // 幂等：同流水号重复回调直接跳过
    }
    if (this.status == PaymentStatus.FAILED || this.status == PaymentStatus.CLOSED) {
      throw new DomainException("已失败或已关闭的支付单无法确认成功");
    }
    if (paidAmount == null || paidAmount.compareTo(this.amount) != 0) {
      throw new DomainException("实付金额与应付金额不一致: paid=" + paidAmount + ", expect=" + this.amount);
    }
    this.status = PaymentStatus.SUCCESS;
    this.channelTradeNo = channelTradeNo;
    this.paidAt = Instant.now();
    this.updatedAt = Instant.now();
    addDomainEvent(
        new PaymentSucceededEvent(
            getId(), getTenantId(), orderId, amount, channelTradeNo, Instant.now()));
    return true;
  }

  /** 渠道明确失败。 */
  public void markFailed(String channelTradeNo) {
    if (this.status == PaymentStatus.SUCCESS) {
      throw new DomainException("已成功的支付单不能标记为失败");
    }
    if (this.status == PaymentStatus.FAILED || this.status == PaymentStatus.CLOSED) {
      return;
    }
    this.status = PaymentStatus.FAILED;
    this.channelTradeNo = channelTradeNo;
    this.updatedAt = Instant.now();
    addDomainEvent(new PaymentFailedEvent(getId(), getTenantId(), orderId, amount, Instant.now()));
  }

  /** 超时未支付 / 订单取消时关闭支付单。 */
  public void close() {
    if (this.status == PaymentStatus.SUCCESS) {
      throw new DomainException("已成功的支付单不能关闭");
    }
    if (this.status == PaymentStatus.CLOSED) {
      return;
    }
    this.status = PaymentStatus.CLOSED;
    this.updatedAt = Instant.now();
  }

  /**
   * 退款（仅已成功支付单可退款）。
   *
   * <p>幂等：已退款的支付单重复退款直接返回（不重复发事件）；支持部分/全额退款，退款金额不可超过已付 金额且必须为正数。
   *
   * @param refundAmount 退款金额（≤ 已付金额）
   * @return 本次调用是否真正执行退款（false 表示幂等跳过）
   */
  public boolean refund(BigDecimal refundAmount) {
    if (this.status != PaymentStatus.SUCCESS) {
      throw new DomainException("只有已成功的支付单可以退款");
    }
    if (this.refundedAt != null) {
      return false; // 幂等：已退款再次退款直接跳过
    }
    if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new DomainException("退款金额必须大于0");
    }
    if (refundAmount.compareTo(this.amount) > 0) {
      throw new DomainException("退款金额不能超过已付金额");
    }
    this.refundAmount = refundAmount;
    this.refundedAt = Instant.now();
    this.updatedAt = Instant.now();
    addDomainEvent(
        new PaymentRefundedEvent(
            getId(), getTenantId(), orderId, refundAmount, this.channelTradeNo, Instant.now()));
    return true;
  }

  /** 是否处于可发起支付状态（新建或支付中，可重试）。 */
  public boolean isPayable() {
    return this.status == PaymentStatus.PENDING || this.status == PaymentStatus.PAYING;
  }

  /** 支付单是否已成功（用于订单确认支付的前置判断）。 */
  public boolean isSuccess() {
    return this.status == PaymentStatus.SUCCESS;
  }
}
