package com.bone.blueprint.infrastructure.extension.order;

import com.bone.blueprint.application.port.out.PricingPort;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.blueprint.domain.extension.order.OrderPriceRequest;
import com.bone.blueprint.domain.gateway.MasterDataGateway;
import com.bone.blueprint.domain.model.shared.valueobject.Money;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.context.ExtensionContextManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link PricingPort} 基础设施实现：装配扩展点运行期上下文并调用 {@link OrderPriceCalculator}。
 *
 * <p>扩展引擎上下文（{@code BizContext} / {@code ExtensionContextManager}）属技术设施，下沉到本适配器； 应用层（{@code
 * OrderApplicationService}）仅依赖 {@link PricingPort} 接口，不再引入 {@code com.bone.engine.*}。 {@code
 * try-with-resources} 在调用后自动复原线程上下文。
 */
@Component
@RequiredArgsConstructor
public class PricingPortAdapter implements PricingPort {

  private final OrderPriceCalculator priceCalculator;
  private final MasterDataGateway masterDataGateway;

  @Override
  public Money calculateFinalPrice(Money baseAmount, long tenantId) {
    return calculateFinalPrice(baseAmount, tenantId, null);
  }

  /**
   * 客户等级感知的计价：等级来自主数据「客户主数据」，决定命中哪个计价场景。
   *
   * <p>过去这里硬编码 {@code scenario("standard")}，VIP/会员/企业三个计价器是永远命不中的死代码； 现在等级由 masterdata
   * 客户主数据驱动（客户未建档/无等级/主数据不可达 → 标准价），扩展点真正「活」了。
   */
  @Override
  public Money calculateFinalPrice(Money baseAmount, long tenantId, String customerCode) {
    OrderPriceRequest request = OrderPriceRequest.of(baseAmount.toBigDecimal());
    String scenario =
        masterDataGateway
            .findCustomerLevelCode(customerCode)
            .map(PricingPortAdapter::levelToScenario)
            .orElse("standard");
    // 扩展点代理按 BizContext（租户 / 业务维度）匹配具体实现；运行期上下文由扩展引擎 ThreadLocal 承载，
    // 调用前建立电商下单场景维度（bizCode=ecommerce/useCase=order/scenario=<等级映射>）。
    BizContext<Void> pricingContext =
        BizContext.<Void>builder()
            .tenant(String.valueOf(tenantId))
            .bizCode("ecommerce")
            .useCase("order")
            .scenario(scenario)
            .build();
    try (var scope = ExtensionContextManager.with(pricingContext)) {
      return Money.of(priceCalculator.calculate(request));
    }
  }

  /** 主数据等级编码 → 扩展点场景编码。 */
  private static String levelToScenario(String levelCode) {
    return switch (levelCode) {
      case "VIP" -> "vip";
      case "MEMBER" -> "member";
      case "ENTERPRISE" -> "enterprise";
      default -> "standard";
    };
  }
}
