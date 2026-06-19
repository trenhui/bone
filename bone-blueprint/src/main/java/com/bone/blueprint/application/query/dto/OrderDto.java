package com.bone.blueprint.application.query.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDto {
  private Long id;
  private Long customerId;
  private BigDecimal totalAmount;
  private String status;
  private LocalDateTime createdAt;
  private List<OrderItemDto> items;

  private OrderDto() {}

  public Long getId() {
    return id;
  }

  public Long getCustomerId() {
    return customerId;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public List<OrderItemDto> getItems() {
    return items;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Long id;
    private Long customerId;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createdAt;
    private List<OrderItemDto> items;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public Builder customerId(Long customerId) {
      this.customerId = customerId;
      return this;
    }

    public Builder totalAmount(BigDecimal totalAmount) {
      this.totalAmount = totalAmount;
      return this;
    }

    public Builder status(String status) {
      this.status = status;
      return this;
    }

    public Builder createdAt(LocalDateTime createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder items(List<OrderItemDto> items) {
      this.items = items;
      return this;
    }

    public OrderDto build() {
      OrderDto dto = new OrderDto();
      dto.id = this.id;
      dto.customerId = this.customerId;
      dto.totalAmount = this.totalAmount;
      dto.status = this.status;
      dto.createdAt = this.createdAt;
      dto.items = this.items;
      return dto;
    }
  }

  public static class OrderItemDto {
    private Long id;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;

    private OrderItemDto() {}

    public Long getId() {
      return id;
    }

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

    public BigDecimal getSubtotal() {
      return subtotal;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private Long id;
      private Long productId;
      private String productName;
      private Integer quantity;
      private BigDecimal unitPrice;
      private BigDecimal subtotal;

      public Builder id(Long id) {
        this.id = id;
        return this;
      }

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

      public Builder subtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
        return this;
      }

      public OrderItemDto build() {
        OrderItemDto dto = new OrderItemDto();
        dto.id = this.id;
        dto.productId = this.productId;
        dto.productName = this.productName;
        dto.quantity = this.quantity;
        dto.unitPrice = this.unitPrice;
        dto.subtotal = this.subtotal;
        return dto;
      }
    }
  }
}
