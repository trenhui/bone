package com.bone.blueprint.domain.model.payment.valueobject;

/** 支付渠道（模拟外部支付服务）。 */
public enum PaymentChannel {
  /** 模拟渠道：本地内存支付（样板默认，无需真实第三方） */
  SIMULATED,
  /** 微信支付（真实接入时替换为渠道适配器） */
  WECHAT,
  /** 支付宝（真实接入时替换为渠道适配器） */
  ALIPAY
}
