package com.bone.blueprint.infrastructure.extension.order;

import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;

@Extension(
    name = "企业订单价格计算",
    description = "企业客户专享的价格计算逻辑",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "enterprise")
public class EnterpriseOrderPriceCalculator extends AbstractRateOrderPriceCalculator {
  /** 企业客户享受7折优惠（本地兜底；主数据 CUSTOMER_LEVEL.ENTERPRISE 优先） */
  @Override
  protected BigDecimal discountRate() {
    return new BigDecimal("0.7");
  }

  /** 主数据等级编码：企业客户 → ENTERPRISE。 */
  @Override
  protected String masterDataLevelCode() {
    return "ENTERPRISE";
  }

  /** 定价规则中心场景编码（与 @Extension scenario 一致）。 */
  @Override
  protected String extensionScenario() {
    return "enterprise";
  }
}
