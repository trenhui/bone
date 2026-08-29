package com.bone.blueprint.adapter.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 发起支付请求 DTO（仅 adapter 层入参，§23.1）。 */
@Data
public class InitiatePaymentReq {

  @NotNull(message = "订单ID不能为空")
  private Long orderId;
}
