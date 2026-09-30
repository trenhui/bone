package com.bone.blueprint.infrastructure.extension.order;

import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;

@Extension(
    name = "VIP订单价格计算",
    description = "VIP客户按主数据等级折扣率计价（兜底9折）",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "vip")
public class VipOrderPriceCalculator extends AbstractRateOrderPriceCalculator {
  /** VIP客户享受9折优惠（本地兜底；主数据 CUSTOMER_LEVEL.VIP 优先） */
  @Override
  protected BigDecimal discountRate() {
    return new BigDecimal("0.9");
  }

  /** 主数据等级编码：VIP → VIP。 */
  @Override
  protected String masterDataLevelCode() {
    return "VIP";
  }

  /** 定价规则中心场景编码（与 @Extension scenario 一致）。 */
  @Override
  protected String extensionScenario() {
    return "vip";
  }
}
