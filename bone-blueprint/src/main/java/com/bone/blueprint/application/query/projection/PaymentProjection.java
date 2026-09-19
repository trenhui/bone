package com.bone.blueprint.application.query.projection;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/**
 * 支付读侧投影行（ADR-0028 读模型，来自 bp_payment 查询）。
 *
 * <p>D0（E-8）约束：domain 包内禁止 {@code @Data}/{@code @Setter}，故只暴露 {@code @Getter} + 全参构造器，由读侧 RowMapper
 * 组装。
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
}
