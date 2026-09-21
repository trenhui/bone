package com.bone.blueprint.application.query.support;

import com.bone.blueprint.application.query.dto.PaymentDto;
import com.bone.blueprint.domain.payment.Payment;

/** 支付详情读模型 → PaymentDto 组装（与 OrderDetailAssembler 同层）。 */
public final class PaymentDetailAssembler {

  private PaymentDetailAssembler() {}

  /**
   * 支付聚合 → DTO。
   *
   * <p><b>为何直接从聚合装配，而不是先转 {@code PaymentProjection}</b>：{@code PaymentDto} 是 {@code bp_payment}
   * 全行的子集，与写聚合字段一一对应——两者之间<strong>没有读模型分歧</strong>， 中间那层投影只是同形搬运。按 ADR-0028「读模型分歧才引入 QueryPort」的判据，
   * 这条读路径直接走 {@code PaymentRepository#findById} + 本方法即可， 既少了转发，也让读路径与写路径共用同一套租户隔离语义（已被 {@code
   * RepositoryTenantIsolationTest} 锁死）。
   *
   * <p>枚举统一落成 {@code name()}：DTO 面向前端，暴露字符串比暴露 Java 枚举名更稳定。
   */
  public static PaymentDto from(Payment payment) {
    return PaymentDto.builder()
        .paymentId(payment.getId())
        .orderId(payment.getOrderId())
        .customerId(payment.getCustomerId())
        .amount(payment.getAmount())
        .channel(payment.getChannel() == null ? null : payment.getChannel().name())
        .status(payment.getStatus() == null ? null : payment.getStatus().name())
        .channelTradeNo(payment.getChannelTradeNo())
        .payUrl(payment.getPayUrl())
        .paidAt(payment.getPaidAt())
        .refundedAt(payment.getRefundedAt())
        .refundAmount(payment.getRefundAmount())
        .createdAt(payment.getCreatedAt())
        .build();
  }
}
