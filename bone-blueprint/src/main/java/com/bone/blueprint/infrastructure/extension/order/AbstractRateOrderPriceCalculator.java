package com.bone.blueprint.infrastructure.extension.order;

import com.bone.blueprint.domain.extension.order.OrderPriceRequest;
import java.math.BigDecimal;

/**
 * 折扣率型计价器的公共基类（DRY）：把「原价 × 折扣率」这一唯一计算式收口到一处。
 *
 * <p><b>为何抽基类而不是各写各的</b>：5 个租户/场景计价器的差异<strong>只有一个折扣率常量</strong>，计算式完全相同。
 * 若各自实现，将来改口径（如引入舍入规则、最小金额、多级折扣）要改 5 处，且任何一处漏改都会造成「同场景不同算法」的资损级不一致。基类把变化点收敛为 {@link
 * #discountRate()}， 实现类只剩一行常量。
 *
 * <p><b>为何不放在 domain</b>：折扣率不是领域不变量，而是各渠道/租户的<strong>技术实现细节</strong>；{@code domain/extension/order}
 * 只保留 {@link OrderPriceCalculator} 这一业务策略接口（零框架依赖），装配与复用细节留在 infrastructure 的扩展实现包内。
 *
 * <p><b>扩展引擎兼容</b>：本基类<strong>不带</strong> {@code @Extension} 注解，不会被扩展点扫描注册；具体实现类仍需各自标注
 * {@code @Extension}（租户/场景维度由注解声明），继承不影响其被发现与实例化。
 */
public abstract class AbstractRateOrderPriceCalculator implements ExtensionOrderPriceCalculator {

  /** 计算式唯一入口：原价 × 折扣率。子类只声明折扣率，不重复计算式。 */
  @Override
  public BigDecimal calculate(OrderPriceRequest request) {
    return request.totalBeforeDiscount().multiply(discountRate());
  }

  /**
   * 折扣率：{@code 0.85} 表示 85 折，{@link BigDecimal#ONE} 表示不打折。
   *
   * <p>用字符串构造（{@code new BigDecimal("0.85")}）而非 double 字面量——后者会带来二进制精度误差， 直接落到金额上即为资损。
   */
  protected abstract BigDecimal discountRate();
}
