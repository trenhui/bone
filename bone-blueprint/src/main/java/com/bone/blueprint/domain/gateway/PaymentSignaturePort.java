package com.bone.blueprint.domain.gateway;

import com.bone.blueprint.domain.payment.Payment;
import java.math.BigDecimal;

/**
 * 支付回调验签端口（防腐层，§19）。
 *
 * <p>真实支付渠道回调必须验签（HMAC/RSA/证书）以防伪造。样板用共享密钥模拟 HMAC 验签，演示「回调先验签 再进领域」的安全边界：验签在 adapter
 * 边界完成，校验通过才把可信结果交给 Handler。
 *
 * <p>实现位于 {@code infrastructure/gateway/payment/}，domain 不依赖具体签名算法/密钥。
 */
public interface PaymentSignaturePort {

  /**
   * 校验回调签名是否可信。
   *
   * @param payment 待确认的支付单（提供 orderId/amount 等参与签名要素）
   * @param paidAmount 实付金额（签名参与要素）
   * @param signature 请求携带的签名
   * @return true=签名可信
   */
  boolean verify(Payment payment, BigDecimal paidAmount, String signature);
}
