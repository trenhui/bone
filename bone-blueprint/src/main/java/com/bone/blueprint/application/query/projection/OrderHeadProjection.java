package com.bone.blueprint.application.query.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 订单头读模型行（单表投影，供超时扫描等轻量查询使用；复杂 Join 用 {@link OrderWithItemsProjection}）。
 *
 * <p>D0（E-8）约束：domain 包内禁止 {@code @Data}/{@code @Setter}，故只暴露 {@code @Getter} + 全参构造器。
 *
 * <p><b>为何补了一个私有无参构造器</b>：本投影由 SDK {@code @Sql} 仓储返回，结果经 {@code SmartRowMapper} 用 {@code
 * BeanUtils.instantiateClass} 创建实例后反射填字段——<strong>必须有可访问的无参构造器</strong>。字段因此去掉 {@code final}（Java
 * 要求 final 字段在每个构造器里都显式赋值）。对外仍只有 getter，不可变语义未破。
 *
 * <p><b>带 {@code tenantId}</b>：定时任务需按扫描到的行<strong>显式携带租户</strong>下发命令（E-2 异步分支必须显式传递租户，
 * 不能依赖线程上下文），否则命令在 Handler 内会落到"平台租户"而查不到该行数据。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderHeadProjection {
  private Long tenantId;
  private Long orderId;
  private Long customerId;
  private BigDecimal totalAmount;
  private String status;
  private LocalDateTime createdAt;

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
