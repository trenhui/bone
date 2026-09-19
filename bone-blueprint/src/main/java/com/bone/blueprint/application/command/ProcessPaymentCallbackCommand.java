package com.bone.blueprint.application.command;

import java.math.BigDecimal;

/**
 * 支付回调命令：支付渠道异步通知支付结果。
 *
 * <p>回调以「支付单号 + 渠道流水号」标识，Handler 内先验签、再幂等确认（含金额一致性校验），避免渠道重复回调重复入账。
 *
 * <p><b>含签名字段</b>：验签必须在 Handler（application 层）完成，否则 adapter 层会直引技术端口（违反 E-4.2 分层约束）。 把验签收口到
 * Handler，HTTP / RPC / MQ 任意入口调用都会自动验签，不会漏。
 *
 * @param paymentId 支付单 ID（回调定位支付单）
 * @param channelTradeNo 渠道流水号（支付成功时回填，幂等去重键）
 * @param paidAmount 渠道实付金额（须与应付金额一致，支付核心不变量）
 * @param success 是否支付成功（由回调报文判定）
 * @param signature 渠道签名（Handler 内验签）
 */
public record ProcessPaymentCallbackCommand(
    Long paymentId,
    String channelTradeNo,
    BigDecimal paidAmount,
    boolean success,
    String signature) {}
