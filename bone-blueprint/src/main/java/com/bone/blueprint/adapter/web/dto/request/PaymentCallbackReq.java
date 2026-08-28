package com.bone.blueprint.adapter.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

/** 支付渠道回调请求 DTO（模拟真实渠道回调报文，§23.1）。 */
@Data
public class PaymentCallbackReq {

  /** 支付单 ID。 */
  @NotNull(message = "支付单ID不能为空")
  private Long paymentId;

  /** 渠道流水号。 */
  @NotBlank(message = "渠道流水号不能为空")
  private String channelTradeNo;

  /** 渠道实付金额（须与应付金额一致）。 */
  @NotNull(message = "实付金额不能为空")
  private BigDecimal paidAmount;

  /** 回调签名（模拟 HMAC，验签通过才可进领域）。 */
  private String signature;

  /** 支付结果：true=成功，false=失败。 */
  private boolean success = true;
}
