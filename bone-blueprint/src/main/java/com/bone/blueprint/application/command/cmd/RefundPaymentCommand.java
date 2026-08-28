package com.bone.blueprint.application.command.cmd;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/** 支付退款命令：对已成功支付单发起退款。 */
@Getter
@Builder
public class RefundPaymentCommand {

  /** 支付单 ID。 */
  private final Long paymentId;

  /** 退款金额（≤ 已付金额，支持部分退款）。 */
  private final BigDecimal refundAmount;
}
