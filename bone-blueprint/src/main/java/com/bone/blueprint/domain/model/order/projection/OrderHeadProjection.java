package com.bone.blueprint.domain.model.order.projection;

import com.bone.blueprint.domain.model.order.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 订单头读模型（单表投影，供超时扫描等轻量查询；Join 场景用 {@link OrderWithItemsProjection}）。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderHeadProjection {
  /** 全租户扫描的调用方据此显式携带租户下发命令（E-2：异步分支不依赖线程上下文）。 */
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

  /** 聚合 → 投影（Criteria 通道用；{@code @Sql} 通道由 {@code SmartRowMapper} 反射填充）。 */
  public static OrderHeadProjection from(Order order) {
    return new OrderHeadProjection(
        order.getTenantId(),
        order.getId(),
        order.getCustomerId(),
        order.getTotalAmount(),
        order.getStatus() == null ? null : order.getStatus().name(),
        // ★ i18n/时区样板：聚合里是 Instant（绝对时刻），投影用 LocalDateTime 承载时**必须显式按 UTC 归一**。
        //   旧写法 ZoneId.systemDefault() 会让同一时刻在不同时区的实例上产出不同值 —— 服务端时区一变，
        //   列表时间与详情时间就对不上，且前端无法还原成正确的本地时间（§6.4）。
        order.getCreatedAt() == null
            ? null
            : LocalDateTime.ofInstant(order.getCreatedAt(), ZoneOffset.UTC));
  }
}
