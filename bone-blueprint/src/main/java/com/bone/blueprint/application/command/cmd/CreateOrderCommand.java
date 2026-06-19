package com.bone.blueprint.application.command.cmd;

import java.math.BigDecimal;
import java.util.List;

public class CreateOrderCommand {
  private Long customerId;
  private List<OrderItemDto> items;

  private CreateOrderCommand() {}

  public Long getCustomerId() {
    return customerId;
  }

  public List<OrderItemDto> getItems() {
    return items;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Long customerId;
    private List<OrderItemDto> items;

    public Builder customerId(Long customerId) {
      this.customerId = customerId;
      return this;
    }

    public Builder items(List<OrderItemDto> items) {
      this.items = items;
      return this;
    }

    public CreateOrderCommand build() {
      CreateOrderCommand command = new CreateOrderCommand();
      command.customerId = this.customerId;
      command.items = this.items;
      return command;
    }
  }

  public static class OrderItemDto {
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;

    private OrderItemDto() {}

    public Long getProductId() {
      return productId;
    }

    public String getProductName() {
      return productName;
    }

    public Integer getQuantity() {
      return quantity;
    }

    public BigDecimal getUnitPrice() {
      return unitPrice;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private Long productId;
      private String productName;
      private Integer quantity;
      private BigDecimal unitPrice;

      public Builder productId(Long productId) {
        this.productId = productId;
        return this;
      }

      public Builder productName(String productName) {
        this.productName = productName;
        return this;
      }

      public Builder quantity(Integer quantity) {
        this.quantity = quantity;
        return this;
      }

      public Builder unitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        return this;
      }

      public OrderItemDto build() {
        OrderItemDto dto = new OrderItemDto();
        dto.productId = this.productId;
        dto.productName = this.productName;
        dto.quantity = this.quantity;
        dto.unitPrice = this.unitPrice;
        return dto;
      }
    }
  }
}
