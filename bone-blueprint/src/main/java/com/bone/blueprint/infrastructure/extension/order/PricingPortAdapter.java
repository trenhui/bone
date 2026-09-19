package com.bone.blueprint.infrastructure.extension.order;

import com.bone.blueprint.application.port.out.PricingPort;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.blueprint.domain.extension.order.OrderPriceRequest;
import com.bone.blueprint.domain.shared.valueobject.Money;
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

  @Override
  public Money calculateFinalPrice(Money baseAmount, long tenantId) {
    OrderPriceRequest request = OrderPriceRequest.of(baseAmount.toBigDecimal());
    // 扩展点代理按 BizContext（租户 / 业务维度）匹配具体实现；运行期上下文由扩展引擎 ThreadLocal 承载，
    // 调用前显式建立电商下单场景维度（bizCode=ecommerce/useCase=order/scenario=standard），命中平台默认计价器。
    BizContext<Void> pricingContext =
        BizContext.<Void>builder()
            .tenant(String.valueOf(tenantId))
            .bizCode("ecommerce")
            .useCase("order")
            .scenario("standard")
            .build();
    try (var scope = ExtensionContextManager.with(pricingContext)) {
      return Money.of(priceCalculator.calculate(request));
    }
  }
}
