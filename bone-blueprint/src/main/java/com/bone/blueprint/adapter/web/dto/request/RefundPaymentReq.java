package com.bone.blueprint.adapter.web.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

/** 支付退款请求 DTO。 */
@Data
public class RefundPaymentReq {

  @NotNull(message = "退款金额不能为空")
  private BigDecimal refundAmount;
}
