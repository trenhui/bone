package com.bone.blueprint.application.command.cmd;

import java.math.BigDecimal;

/**
 * 支付回调命令：支付渠道异步通知支付结果。
 *
 * <p>回调以「支付单号 + 渠道流水号」标识，Handler 内幂等确认（含金额一致性校验），避免渠道重复回调重复 入账。
 *
 * <p><b>不含签名字段</b>：验签已在 adapter 边界完成（ADR-0022），本命令只承载"已验签"的业务意图—— 把 signature 带进应用层只会让人误以为 Handler
 * 会验签，从而为新的入站通道埋下漏验签的隐患。
 *
 * @param paymentId 支付单 ID（回调定位支付单）
 * @param channelTradeNo 渠道流水号（支付成功时回填，幂等去重键）
 * @param paidAmount 渠道实付金额（须与应付金额一致，支付核心不变量）
 * @param success 是否支付成功（由已验签的回调报文判定）
 */
public record ProcessPaymentCallbackCommand(
    Long paymentId, String channelTradeNo, BigDecimal paidAmount, boolean success) {}
