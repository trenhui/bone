package com.bone.blueprint.application.command;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建订单命令（不可变 record，含订单项列表）。
 *
 * @param customerId 客户 ID
 * @param items 订单项
 */
public record CreateOrderCommand(Long customerId, List<OrderItemDto> items) {

  /** 订单项（命令入参，不可变）。 */
  public record OrderItemDto(
      Long productId, String productName, Integer quantity, BigDecimal unitPrice) {}
}
