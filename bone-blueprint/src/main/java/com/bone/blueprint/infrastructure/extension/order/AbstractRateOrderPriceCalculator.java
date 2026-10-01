package com.bone.blueprint.infrastructure.extension.order;

import com.bone.blueprint.domain.extension.order.OrderPriceRequest;
import com.bone.blueprint.domain.gateway.MasterDataGateway;
import com.bone.blueprint.domain.gateway.PricingRuleGateway;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

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
 *
 * <p><b>主数据驱动折扣（真实场景）</b>：折扣率的治理真源是 bone-masterdata 的「客户等级折扣率」主数据 （等级编码 →
 * 折扣率）。计价时优先查主数据（运营在主数据侧改折扣率，下单即时生效、无需发版）； 主数据未配置该等级或服务不可达时，退回 {@link #discountRate()} 本地常量兜底。字段注入且
 * {@code required = false}：扩展实例若由扩展引擎反射创建（非 Spring 装配），网关为 null，自然走常量兜底，不破坏引擎兼容。
 */
@Slf4j
public abstract class AbstractRateOrderPriceCalculator implements ExtensionOrderPriceCalculator {

  /** 主数据网关（可缺省：反射实例化时为 null，走本地常量兜底）。 */
  @Autowired(required = false)
  private MasterDataGateway masterDataGateway;

  /** 定价规则中心网关（可缺省：反射实例化时为 null，跳过规则中心）。 */
  @Autowired(required = false)
  private PricingRuleGateway pricingRuleGateway;

  /**
   * 本实现对应的定价场景编码（与 {@code @Extension(scenario=...)} 保持一致，作为定价规则中心的业务键）。
   *
   * <p>默认从 {@link org.springframework.beans.factory.annotation.Value} 不可行（非 Bean 属性）， 由各实现类覆盖返回；返回
   * null 表示不查规则中心。
   */
  protected String extensionScenario() {
    return null;
  }

  /** 计算式唯一入口：原价 × 折扣率。折扣率主数据优先，常量兜底。 */
  @Override
  public BigDecimal calculate(OrderPriceRequest request) {
    return request.totalBeforeDiscount().multiply(resolveDiscountRate());
  }

  /**
   * 折扣率解析（三级回退）：① masterdata「客户等级折扣率」（治理型主数据，本期主数据驱动定价的真源）→ ② 元数据「定价规则中心」 （bone-metadata-server
   * PRICING_RULE，运营按 scenario 的额外叠加规则）→ ③ 本地常量。
   *
   * <p><b>优先级调整说明（BP-PRICING-1）</b>：原序为「规则中心 > 主数据」，但运行期规则中心（tenant 1001 的 PRICING_RULE）实际持有与主数据
   * {@code CUSTOMER_LEVEL} 不一致的取值（如 vip=0.8 而非 0.88），导致主数据折扣率被架空、 客户档位定价错乱。本期目标为「bone-masterdata
   * 驱动定价」，故将主数据置为最高优先级——运营在主数据侧改折扣率即下单即时生效； 规则中心保留为「主数据未建模该档位时的补充层」。
   *
   * <p>0.85 表示 85 折；{@link BigDecimal#ONE} 表示不打折。各来源保持「乘法折扣」同一口径。
   */
  protected BigDecimal resolveDiscountRate() {
    String levelCode = masterDataLevelCode();
    if (levelCode != null && masterDataGateway != null) {
      try {
        Optional<BigDecimal> rate = masterDataGateway.findLevelDiscountRate(levelCode);
        if (rate.isPresent()) {
          return rate.get();
        }
        log.info("主数据未配置等级折扣率，降级定价规则中心: levelCode={}", levelCode);
      } catch (Exception e) {
        log.warn("查询主数据等级折扣率异常，降级定价规则中心: levelCode={}", levelCode, e);
      }
    }
    String scenario = extensionScenario();
    if (scenario != null && pricingRuleGateway != null) {
      try {
        Optional<BigDecimal> ruleRate = pricingRuleGateway.findScenarioDiscountRate(scenario);
        if (ruleRate.isPresent()) {
          return ruleRate.get();
        }
      } catch (Exception e) {
        log.warn("查询定价规则中心异常，降级本地常量: scenario={}", scenario, e);
      }
    }
    return discountRate();
  }

  /**
   * 折扣率本地兜底常量：{@code 0.85} 表示 85 折，{@link BigDecimal#ONE} 表示不打折。
   *
   * <p>用字符串构造（{@code new BigDecimal("0.85")}）而非 double 字面量——后者会带来二进制精度误差， 直接落到金额上即为资损。
   */
  protected abstract BigDecimal discountRate();

  /**
   * 本场景对应的客户等级编码（主数据「客户等级折扣率」实体的业务键）。
   *
   * <p>返回 null 表示该场景无等级语义（如标准价），不查主数据。
   */
  protected String masterDataLevelCode() {
    return null;
  }
}
