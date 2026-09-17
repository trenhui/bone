package com.bone.blueprint.application.port.out;

import java.math.BigDecimal;

/**
 * 回调验签出站端口（E-10 / ADR-0022 / E-10.2）。
 *
 * <p>技术 concern（安全验签），不属于业务网关（domain/gateway 中的 InventoryGateway、PaymentGateway 才是"业务规则依赖的外部事实"）。
 * 故放 {@code application/port/out}。
 *
 * <p><b>入参只含回调报文中的标量</b>（支付单号、渠道流水号、金额、签名），<strong>不依赖 {@code Payment} 聚合</strong>：
 * 验签是对<strong>报文字段</strong>做签名校验，与领域对象无关。这样的签名才能让 adapter 在<strong>进入应用层之前 </strong>完成验签（防腐层职责），也避免
 * adapter 为了验签去加载聚合。
 *
 * <p><b>覆盖范围</b>：所有渠道回调（成功 / 失败 / 关闭）都必须验签——只验签成功回调是错误示范： 伪造的失败回调可把支付单打成终态，随后真实的成功回调被聚合拒绝。
 */
public interface PaymentSignaturePort {

  /**
   * 校验回调签名是否可信。
   *
   * @param paymentId 支付单号（报文字段）
   * @param channelTradeNo 渠道流水号（报文字段）
   * @param amount 报文中声明的金额
   * @param signature 渠道签名
   * @return true 表示签名可信
   */
  boolean verify(long paymentId, String channelTradeNo, BigDecimal amount, String signature);
}
