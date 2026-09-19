package com.bone.blueprint.domain.gateway;

import java.math.BigDecimal;

/**
 * 支付渠道出站端口（防腐层 ACL，E-4.3）。
 *
 * <p>用领域语言声明「预下单」能力，隐藏具体渠道（微信/支付宝/模拟）协议细节。实现位于 {@code
 * infrastructure/gateway/payment/}，application/domain 不依赖第三方实现类型。
 *
 * <p>入参/出参均为领域类型或基础类型，禁止把渠道 DTO/异常穿透到领域层。
 */
public interface PaymentGateway {

  /**
   * 向支付渠道预下单：返回渠道支付链接，用户据此完成支付。
   *
   * @param paymentId 支付单号
   * @param orderId 关联订单号
   * @param amount 支付金额
   * @return 支付链接（用户跳转完成支付）
   */
  String preCreatePayment(Long paymentId, Long orderId, BigDecimal amount);
}
