package com.bone.blueprint.application.command.cmd;

/** 发起支付结果：支付单号 + 支付链接。 */
public record InitiatePaymentResult(Long paymentId, String payUrl) {}
