package com.bone.blueprint.domain.extension.order;

import com.bone.engine.extension.api.annotation.ExtensionPoint;
import java.math.BigDecimal;

@ExtensionPoint(
    name = "订单价格计算扩展点",
    description = "不同租户和场景下的订单价格计算",
    version = "1.0.0",
    transactional = false,
    timeout = 10,
    singleton = true)
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
