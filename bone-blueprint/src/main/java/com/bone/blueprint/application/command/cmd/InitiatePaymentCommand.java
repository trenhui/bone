package com.bone.blueprint.application.command.cmd;

/** 订单发起支付命令：加载订单生成支付单并提交支付渠道。 */
public record InitiatePaymentCommand(Long orderId) {}
