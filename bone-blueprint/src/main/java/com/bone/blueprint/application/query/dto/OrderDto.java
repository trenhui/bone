package com.bone.blueprint.application.query.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** 订单详情读侧 DTO（展示给前端的完整结构，由 {@code OrderDetailAssembler} / {@code OrderSummaryAssembler} 组装）。 */
@Getter
@Setter
@Builder
public class OrderDto {

  private Long id;
  private Long customerId;
  private BigDecimal totalAmount;
  private String status;
  private LocalDateTime createdAt;
  private List<OrderItemDto> items;

  /** 订单来源渠道码（落库值，来自字典 source_channel）。 */
  private String channelSource;

  /** 订单来源渠道中文名（读路径由字典解析，字典不可达时为 null）。 */
  private String channelSourceName;

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
