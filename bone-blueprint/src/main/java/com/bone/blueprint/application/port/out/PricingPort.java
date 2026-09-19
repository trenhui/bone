package com.bone.blueprint.application.port.out;

import com.bone.blueprint.domain.shared.valueobject.Money;

/**
 * 定价服务端口（应用层出站端口）。
 *
 * <p><b>为何存在</b>：下单需按扩展点（会员 / VIP / 促销 / 企业等维度）计算最终金额，而扩展点运行期上下文 （{@code BizContext} + {@code
 * ExtensionContextManager}）属扩展引擎内部机制。把「装配上下文 + 调用扩展点」收口到本端口， 应用层只调用 {@link
 * #calculateFinalPrice}，不再直连 {@code com.bone.engine.*}，降低耦合、便于纯单测。
 *
 * <p>聚合只认 {@link Money}：本端口返回最终金额，应用层交给 {@code Order#applyPricing(Money)}，领域层零感知扩展点接口。
 */
public interface PricingPort {

  /**
   * 基于基准金额计算订单最终应付金额。
   *
   * @param baseAmount 订单原始金额
   * @param tenantId 租户（用于扩展点维度匹配）
   * @return 最终金额（已含扩展点计价结果）
   */
  Money calculateFinalPrice(Money baseAmount, long tenantId);
}
