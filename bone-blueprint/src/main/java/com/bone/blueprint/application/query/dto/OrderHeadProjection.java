package com.bone.blueprint.application.query.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 订单头读模型行（单表投影，供超时扫描等轻量查询使用；复杂 Join 用 {@link OrderWithItemsProjection}）。
 *
 * <p>D0（E-8）约束：domain 包内禁止 {@code @Data}/{@code @Setter}，故只暴露 {@code @Getter} + 全参构造器，由读侧 RowMapper
 * 组装。
 *
 * <p><b>带 {@code tenantId}</b>：定时任务需按扫描到的行<strong>显式携带租户</strong>下发命令（E-4.4 异步分支必须显式传递租户，
 * 不能依赖线程上下文），否则命令在 Handler 内会落到"平台租户"而查不到该行数据。
 */
@Getter
public class OrderHeadProjection {
  private final Long tenantId;
  private final Long orderId;
  private final Long customerId;
  private final BigDecimal totalAmount;
  private final String status;
  private final LocalDateTime createdAt;

  public OrderHeadProjection(
      Long tenantId,
      Long orderId,
      Long customerId,
      BigDecimal totalAmount,
      String status,
      LocalDateTime createdAt) {
    this.tenantId = tenantId;
    this.orderId = orderId;
    this.customerId = customerId;
    this.totalAmount = totalAmount;
    this.status = status;
    this.createdAt = createdAt;
  }
}
