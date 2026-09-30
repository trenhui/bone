package com.bone.blueprint.infrastructure.extension.order;

import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;

@Extension(
    name = "会员订单价格计算",
    description = "会员专享的价格计算逻辑",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "member")
public class MemberOrderPriceCalculator extends AbstractRateOrderPriceCalculator {
  /** 会员享受85折优惠（本地兜底；主数据 CUSTOMER_LEVEL.MEMBER 优先） */
  @Override
  protected BigDecimal discountRate() {
    return new BigDecimal("0.85");
  }

  /** 主数据等级编码：会员 → MEMBER。 */
  @Override
  protected String masterDataLevelCode() {
    return "MEMBER";
  }

  /** 定价规则中心场景编码（与 @Extension scenario 一致）。 */
  @Override
  protected String extensionScenario() {
    return "member";
  }
}
