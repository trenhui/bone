package com.bone.blueprint.application.command.cmd;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * 支付回调命令：支付渠道异步通知支付结果。
 *
 * <p>回调以「支付单号 + 渠道流水号」标识，Handler 内幂等确认（含金额一致性校验），避免渠道重复回调重复 入账。
 */
@Getter
@Builder
public class HandlePaymentCallbackCommand {

  /** 支付单 ID（回调定位支付单）。 */
  private final Long paymentId;

  /** 渠道流水号（支付成功时回填，幂等去重键）。 */
  private final String channelTradeNo;

  /** 渠道实付金额（须与应付金额一致，支付核心不变量）。 */
  private final BigDecimal paidAmount;

  /** 回调签名（模拟 HMAC，验签通过才可进领域）。 */
  private final String signature;

  /** 是否支付成功（真实渠道按回调签名校验结果，此处由调用方判定）。 */
  private final boolean success;
}
