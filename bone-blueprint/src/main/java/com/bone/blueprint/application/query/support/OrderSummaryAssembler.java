package com.bone.blueprint.application.query.support;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.domain.model.order.projection.OrderHeadProjection;

/** 读模型行 → 查询 DTO 组装（application 查询层职责，与 OrderDetailAssembler/PaymentDetailAssembler 同层）。 */
public final class OrderSummaryAssembler {

  private OrderSummaryAssembler() {}

  /** 读模型行 → DTO（分页投影走 *QueryPort，不再反序列化写聚合）。 */
  public static OrderDto fromRow(OrderHeadProjection row) {
    return OrderDto.builder()
        .id(row.getOrderId())
        .customerId(row.getCustomerId())
        .totalAmount(row.getTotalAmount())
        .status(row.getStatus())
        .createdAt(row.getCreatedAt())
        .build();
  }
}
