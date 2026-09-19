package com.bone.blueprint.application.query.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** 订单详情读侧 DTO（展示给前端的完整结构，由 {@code OrderDetailAssembler} / {@code OrderSummaryAssembler} 组装）。 */
@Getter
@Builder
public class OrderDto {

  private Long id;
  private Long customerId;
  private BigDecimal totalAmount;
  private String status;
  private LocalDateTime createdAt;
  private List<OrderItemDto> items;

  @Getter
  @Builder
  public static class OrderItemDto {
    private Long id;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
  }
}
