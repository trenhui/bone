package com.bone.blueprint.domain.order.valueobject;

public enum OrderStatus {
  CREATED, // 已创建
  PAID, // 已支付
  SHIPPED, // 已发货
  DELIVERED, // 已送达
  CANCELLED, // 已取消
  REFUNDED // 已退款
}
