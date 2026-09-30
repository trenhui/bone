package com.bone.blueprint.application.command;

/**
 * 订单发起支付命令：加载订单生成支付单并提交支付渠道。
 *
 * @param currency 结算币种（空值回落 CNY；跨境场景由下单侧透传订单币种）
 */
public record InitiatePaymentCommand(Long orderId, String currency) {

  public InitiatePaymentCommand(Long orderId) {
    this(orderId, null);
  }
}
