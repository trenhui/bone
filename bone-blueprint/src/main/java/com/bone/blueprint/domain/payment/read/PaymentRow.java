package com.bone.blueprint.domain.payment.read;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/** 支付读侧投影行（§18.5 读模型，来自 bp_payment 查询）。 */
@Data
public class PaymentRow {

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
