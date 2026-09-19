package com.bone.blueprint.application.query.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 订单+明细 Join 读模型行（扁平投影，由读侧 SQL 映射）。
 *
 * <p>D0（E-8）约束：domain 包内禁止 {@code @Data}/{@code @Setter}，故只暴露 {@code @Getter} + 全参构造器。
 *
 * <p><b>为何补了一个私有无参构造器</b>：本投影由 SDK {@code @Sql} 仓储返回，结果经 {@code SmartRowMapper} 用 {@code
 * BeanUtils.instantiateClass} 创建实例后反射填字段——<strong>必须有可访问的无参构造器</strong>，字段因此去掉 {@code final}。对外仍只有
 * getter。
 *
 * <p><b>列别名必须匹配字段名</b>：{@code SmartRowMapper} 按列标签找字段（下划线转驼峰），故 SQL 里要写 {@code o.id AS order_id}
 * 这类显式别名；明细列在无明细行时为 {@code null}。
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
