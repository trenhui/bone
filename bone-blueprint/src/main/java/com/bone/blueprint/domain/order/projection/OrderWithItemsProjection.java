package com.bone.blueprint.domain.order.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 订单 + 明细 Join 读模型（扁平行集：每明细一行、头字段逐行重复；无明细时明细列为 {@code null}，折叠责任只在 {@code OrderDetailAssembler}）。
 *
 * <p>两个 SDK 约束：① 必须有可访问的无参构造器（{@code SmartRowMapper} 反射填充），故字段非 {@code final}、对外只暴露 getter；② SQL
 * 列名必须写成与字段一致的显式别名（{@code o.id AS order_id}）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderWithItemsProjection {
  private Long orderId;
  private Long customerId;
  private BigDecimal totalAmount;
  private String status;
  private LocalDateTime createdAt;
  private Long itemId;
  private Long productId;
  private String productName;
  private Integer quantity;
  private BigDecimal unitPrice;
  private BigDecimal subtotal;

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
