package com.bone.blueprint.application.query.support;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.dto.OrderHeadRow;
import com.bone.blueprint.domain.order.Order;

/** 订单聚合 / 读模型 → 查询 DTO 组装（application 查询层职责，与 OrderDetailAssembler/PaymentDetailAssembler 同层）。 */
public final class OrderSummaryAssembler {

  private OrderSummaryAssembler() {}

  public static OrderDto fromOrder(Order order) {
    return OrderDto.builder()
        .id(order.getId())
        .customerId(order.getCustomerId())
        .totalAmount(order.getTotalAmount())
        .status(order.getStatus().name())
        .build();
  }

  /** 读模型行 → DTO（分页投影走 *ReadPort，不再反序列化写聚合，§18.5）。 */
  public static OrderDto fromRow(OrderHeadRow row) {
    return OrderDto.builder()
        .id(row.getOrderId())
        .customerId(row.getCustomerId())
        .totalAmount(row.getTotalAmount())
        .status(row.getStatus())
        .createdAt(row.getCreatedAt())
        .build();
  }
}
