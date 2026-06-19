package com.bone.blueprint.domain.order.read;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 订单+明细 Join 读模型行（扁平投影，由读侧 SQL 映射）。 */
@Data
public class OrderWithItemsRow {
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
}
