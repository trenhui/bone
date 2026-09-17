package com.bone.blueprint.application.query.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 订单+明细 Join 读模型行（扁平投影，由读侧 SQL 映射）。
 *
 * <p>D0（E-8）约束：domain 包内禁止 {@code @Data}/{@code @Setter}，故只暴露 {@code @Getter} + 全参构造器，由读侧 RowMapper
 * 组装。明细列在无明细行时为 {@code null}。
 */
@Getter
public class OrderWithItemsProjection {
  private final Long orderId;
  private final Long customerId;
  private final BigDecimal totalAmount;
  private final String status;
  private final LocalDateTime createdAt;
  private final Long itemId;
  private final Long productId;
  private final String productName;
  private final Integer quantity;
  private final BigDecimal unitPrice;
  private final BigDecimal subtotal;

  public OrderWithItemsProjection(
      Long orderId,
      Long customerId,
      BigDecimal totalAmount,
      String status,
      LocalDateTime createdAt,
      Long itemId,
      Long productId,
      String productName,
      Integer quantity,
      BigDecimal unitPrice,
      BigDecimal subtotal) {
    this.orderId = orderId;
    this.customerId = customerId;
    this.totalAmount = totalAmount;
    this.status = status;
    this.createdAt = createdAt;
    this.itemId = itemId;
    this.productId = productId;
    this.productName = productName;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.subtotal = subtotal;
  }
}
