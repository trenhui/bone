package com.bone.blueprint.domain.payment.projection;

import com.bone.blueprint.domain.payment.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/**
 * 支付单读模型（单表投影，供全租户运维扫描）。
 *
 * <p><b>为何在 {@code domain/payment/projection} 而非 {@code application/query/projection}</b>：本投影由
 * {@link com.bone.blueprint.domain.repository.PaymentRepository} 声明的方法返回。domain 层不能反向依赖 application
 * / infrastructure，故凡域仓储返回的读模型类型必须落在 domain 内；这与 {@link
 * com.bone.blueprint.domain.order.projection.OrderHeadProjection} 同源（ADR-0030 合并形态，E-13.1）。
 *
 * <p>D0（E-8）约束：domain 包内禁止 {@code @Data}/{@code @Setter}，故只暴露 {@code @Getter} + 全参构造器，由读侧组装。
 *
 * <p><b>带 {@code tenantId}</b>：定时任务需按扫描到的行<strong>显式携带租户</strong>下发命令（E-2 异步分支必须显式传递租户）。
 */
@Getter
public class PaymentProjection {

  private final Long tenantId;
  private final Long paymentId;
  private final Long orderId;
  private final Long customerId;
  private final BigDecimal amount;
  private final String channel;
  private final String status;
  private final String channelTradeNo;
  private final String payUrl;
  private final Instant paidAt;
  private final Instant refundedAt;
  private final BigDecimal refundAmount;
  private final Instant createdAt;

  public PaymentProjection(
      Long tenantId,
      Long paymentId,
      Long orderId,
      Long customerId,
      BigDecimal amount,
      String channel,
      String status,
      String channelTradeNo,
      String payUrl,
      Instant paidAt,
      Instant refundedAt,
      BigDecimal refundAmount,
      Instant createdAt) {
    this.tenantId = tenantId;
    this.paymentId = paymentId;
    this.orderId = orderId;
    this.customerId = customerId;
    this.amount = amount;
    this.channel = channel;
    this.status = status;
    this.channelTradeNo = channelTradeNo;
    this.payUrl = payUrl;
    this.paidAt = paidAt;
    this.refundedAt = refundedAt;
    this.refundAmount = refundAmount;
    this.createdAt = createdAt;
  }

  /** 聚合 → 投影（Criteria 通道用；全租户扫描方法即走此路径，不解析 SQL 结果集）。 */
  public static PaymentProjection from(Payment payment) {
    return new PaymentProjection(
        payment.getTenantId(),
        payment.getId(),
        payment.getOrderId(),
        payment.getCustomerId(),
        payment.getAmount(),
        payment.getChannel() == null ? null : payment.getChannel().name(),
        payment.getStatus() == null ? null : payment.getStatus().name(),
        payment.getChannelTradeNo(),
        payment.getPayUrl(),
        payment.getPaidAt(),
        payment.getRefundedAt(),
        payment.getRefundAmount(),
        payment.getCreatedAt());
  }
}
