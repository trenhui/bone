package com.bone.blueprint.application.query.support;

import com.bone.blueprint.application.query.dto.PaymentDto;
import com.bone.blueprint.application.query.dto.PaymentProjection;

/** 支付详情读模型 → PaymentDto 组装（与 OrderDetailAssembler 同层）。 */
public final class PaymentDetailAssembler {

  private PaymentDetailAssembler() {}

  public static PaymentDto from(PaymentProjection row) {
    return PaymentDto.builder()
        .paymentId(row.getPaymentId())
        .orderId(row.getOrderId())
        .customerId(row.getCustomerId())
        .amount(row.getAmount())
        .channel(row.getChannel())
        .status(row.getStatus())
        .channelTradeNo(row.getChannelTradeNo())
        .payUrl(row.getPayUrl())
        .paidAt(row.getPaidAt())
        .refundedAt(row.getRefundedAt())
        .refundAmount(row.getRefundAmount())
        .createdAt(row.getCreatedAt())
        .build();
  }
}
