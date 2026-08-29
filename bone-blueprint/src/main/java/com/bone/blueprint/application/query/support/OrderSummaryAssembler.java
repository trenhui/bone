package com.bone.blueprint.application.query.support;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.domain.order.Order;

/** 订单聚合 → 查询 DTO 组装（application 查询层职责，与 OrderDetailAssembler/PaymentAssemblerHelper 同层）。 */
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
}
