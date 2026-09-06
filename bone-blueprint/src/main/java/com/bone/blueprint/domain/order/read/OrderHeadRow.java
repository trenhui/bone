package com.bone.blueprint.domain.order.read;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 订单头读模型行（单表投影，供超时扫描等轻量查询使用；复杂 Join 用 {@link OrderWithItemsRow}）。
 *
 * <p>D0（E-8）约束：domain 包内禁止 {@code @Data}/{@code @Setter}，故只暴露 {@code @Getter} + 全参构造器，由读侧 RowMapper
 * 组装。
 */
@Getter
public class OrderHeadRow {
  private final Long orderId;
  private final Long customerId;
  private final BigDecimal totalAmount;
  private final String status;
  private final LocalDateTime createdAt;

  public OrderHeadRow(
      Long orderId,
      Long customerId,
      BigDecimal totalAmount,
      String status,
      LocalDateTime createdAt) {
    this.orderId = orderId;
    this.customerId = customerId;
    this.totalAmount = totalAmount;
    this.status = status;
    this.createdAt = createdAt;
  }
}
