package com.bone.blueprint.application.query.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/** 支付单查询结果 DTO（读侧）。 */
@Getter
@Builder
public class PaymentDto {

  private Long paymentId;
  private Long orderId;
  private Long customerId;
  private BigDecimal amount;
  private String channel;
  private String status;
  private String channelTradeNo;
  private String payUrl;
  private Instant paidAt;
  private Instant refundedAt;
  private BigDecimal refundAmount;
  private Instant createdAt;
}
