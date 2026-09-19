package com.bone.blueprint.application.query.support;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.projection.OrderWithItemsProjection;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import java.util.ArrayList;
import java.util.List;

public final class OrderDetailAssembler {

  private OrderDetailAssembler() {}

  public static OrderDto fromRows(List<OrderWithItemsProjection> rows) {
    if (rows == null || rows.isEmpty()) {
      throw BlueprintErrors.of(BlueprintErrorCodes.ORDER_NOT_FOUND);
    }
    OrderWithItemsProjection head = rows.get(0);
    List<OrderDto.OrderItemDto> items = new ArrayList<>();
    for (OrderWithItemsProjection row : rows) {
      if (row.getItemId() != null) {
        items.add(
            OrderDto.OrderItemDto.builder()
                .id(row.getItemId())
                .productId(row.getProductId())
                .productName(row.getProductName())
                .quantity(row.getQuantity())
                .unitPrice(row.getUnitPrice())
                .subtotal(row.getSubtotal())
                .build());
      }
    }
    return OrderDto.builder()
        .id(head.getOrderId())
        .customerId(head.getCustomerId())
        .totalAmount(head.getTotalAmount())
        .status(head.getStatus())
        .createdAt(head.getCreatedAt())
        .items(items)
        .build();
  }
}
