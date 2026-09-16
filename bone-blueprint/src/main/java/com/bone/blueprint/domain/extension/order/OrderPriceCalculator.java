package com.bone.blueprint.domain.extension.order;

import java.math.BigDecimal;

/**
 * 订单价格计算策略（业务端口，domain 层纯净）。
 *
 * <p>扩展引擎的技术契约（{@code @ExtensionPoint} / {@code @Extension}）下沉到 {@code
 * infrastructure/extension/order/ExtensionOrderPriceCalculator} 子接口——本接口只承载"订单可以有不同的定价策略"
 * 这一业务概念，domain 层不感知扩展引擎框架的存在。
 */
public interface OrderPriceCalculator {
  BigDecimal calculate(OrderPriceRequest request);

  public static class OrderPriceRequest {
    private BigDecimal baseAmount;
    private BigDecimal shippingFee;

    private OrderPriceRequest() {}

    public static Builder builder() {
      return new Builder();
    }

    public BigDecimal getBaseAmount() {
      return baseAmount;
    }

    public BigDecimal getShippingFee() {
      return shippingFee;
    }

    public static class Builder {
      private BigDecimal baseAmount;
      private BigDecimal shippingFee = BigDecimal.ZERO;

      public Builder baseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
        return this;
      }

      public Builder shippingFee(BigDecimal shippingFee) {
        this.shippingFee = shippingFee;
        return this;
      }

      public OrderPriceRequest build() {
        OrderPriceRequest request = new OrderPriceRequest();
        request.baseAmount = this.baseAmount;
        request.shippingFee = this.shippingFee;
        return request;
      }
    }
  }
}
