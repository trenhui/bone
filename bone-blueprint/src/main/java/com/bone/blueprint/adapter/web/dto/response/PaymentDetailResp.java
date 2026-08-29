package com.bone.blueprint.adapter.web.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/** 支付单详情响应 DTO。 */
@Data
public class PaymentDetailResp {

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
