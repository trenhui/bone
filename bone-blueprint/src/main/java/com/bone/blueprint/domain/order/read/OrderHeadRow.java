package com.bone.blueprint.domain.order.read;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 订单头读模型行（单表投影，供超时扫描等轻量查询使用；复杂 Join 用 {@link OrderWithItemsRow}）。 */
@Data
public class OrderHeadRow {
  private Long orderId;
  private Long customerId;
  private BigDecimal totalAmount;
  private String status;
  private LocalDateTime createdAt;
}
