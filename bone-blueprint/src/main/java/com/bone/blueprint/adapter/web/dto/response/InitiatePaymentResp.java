package com.bone.blueprint.adapter.web.dto.response;

import lombok.Data;

/** 发起支付响应 DTO：返回支付单号与支付链接。 */
@Data
public class InitiatePaymentResp {

  private Long paymentId;

  private String payUrl;
}
