package com.bone.blueprint.domain.extension.order;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;

/**
 * 订单价格计算的输入参数（领域值对象，不可变）。
 *
 * <p><b>为何独立成文件而不是嵌在 {@link OrderPriceCalculator} 里</b>：它是仓库里唯一一个「接口内嵌 public static
 * class」的领域类型。两个问题：① 阅读成本——扩展点实现方（{@code infrastructure/extension/order/*}）与测试都必须写 {@code
 * OrderPriceCalculator.OrderPriceRequest} 才能引用它，而它本身就是契约的一部分，值得独立可见；② 值语义缺失——原内嵌类字段非 {@code
 * final}、手写 {@code Builder}、无 {@code equals}/{@code hashCode}，名字叫 Request 却不是值对象。 模块内其它 domain
 * 值对象（{@code Money}、{@code OrderStatus}）都独立成文件，这里对齐。
 *
 * <p><b>为何用 record 而不是像 {@code Money} 那样手写 final class</b>：判据是**有没有行为与不变量**。{@code Money}
 * 承载金额不变量与算术行为，手写更清楚；本类是纯数据载体（模块内命令、集成事件、DTO 同样一律用 record），record 直接给出不可变、{@code equals}/{@code
 * hashCode} 与规范访问器，手写只会多出与语义无关的样板。
 *
 * <p><b>不变量</b>：{@code baseAmount} 必须非空（原实现允许传 null，直到计价器调用 {@code getBaseAmount().add(...)} 才 以
 * NPE 暴露，错误点离成因很远）；{@code shippingFee} 可空并按 {@link BigDecimal#ZERO} 归一，避免每个计价器各自判空。
 *
 * @param baseAmount 订单基准金额（必填）
 * @param shippingFee 运费（可空，按 {@code 0} 处理）
 */
public record OrderPriceRequest(BigDecimal baseAmount, BigDecimal shippingFee) {

  public OrderPriceRequest {
    if (baseAmount == null) {
      throw new DomainException("订单基准金额不能为空");
    }
    shippingFee = shippingFee == null ? BigDecimal.ZERO : shippingFee;
  }

  /** 不含运费的价格计算入参。 */
  public static OrderPriceRequest of(BigDecimal baseAmount) {
    return new OrderPriceRequest(baseAmount, BigDecimal.ZERO);
  }

  /** 含运费的价格计算入参（{@code shippingFee} 传 {@code null} 等价于 {@link #of(BigDecimal)}）。 */
  public static OrderPriceRequest of(BigDecimal baseAmount, BigDecimal shippingFee) {
    return new OrderPriceRequest(baseAmount, shippingFee);
  }

  /** 基准金额与运费之和（各计价器共用的第一步；{@code shippingFee} 已由构造器归一，不必再判空）。 */
  public BigDecimal totalBeforeDiscount() {
    return baseAmount.add(shippingFee);
  }
}
