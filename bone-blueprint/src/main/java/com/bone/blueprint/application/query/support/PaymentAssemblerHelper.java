package com.bone.blueprint.application.query.support;

import com.bone.blueprint.application.query.dto.PaymentDto;
import com.bone.blueprint.domain.payment.read.PaymentRow;

/** 支付读模型 → 查询 DTO 组装（application 查询层职责）。 */
public final class PaymentAssemblerHelper {

  private PaymentAssemblerHelper() {}

  public static PaymentDto toDto(PaymentRow row) {
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
