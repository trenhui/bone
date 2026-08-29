package com.bone.blueprint.application.command.cmd;

import java.math.BigDecimal;

/**
 * 支付回调命令：支付渠道异步通知支付结果。
 *
 * <p>回调以「支付单号 + 渠道流水号」标识，Handler 内幂等确认（含金额一致性校验），避免渠道重复回调重复 入账。
 *
 * @param paymentId 支付单 ID（回调定位支付单）
 * @param channelTradeNo 渠道流水号（支付成功时回填，幂等去重键）
 * @param paidAmount 渠道实付金额（须与应付金额一致，支付核心不变量）
 * @param signature 回调签名（模拟 HMAC，验签通过才可进领域）
 * @param success 是否支付成功（真实渠道按回调签名校验结果，此处由调用方判定）
 */
public record HandlePaymentCallbackCommand(
    Long paymentId,
    String channelTradeNo,
    BigDecimal paidAmount,
    String signature,
    boolean success) {}
