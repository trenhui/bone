package com.bone.procurement.transaction.domain.model.po;

/** 采购订单领域事件（ACKNOWLEDGE 为履约切片受控解锁的唯一迁移；其余履约段事件保留枚举但无入口）。 */
public enum PoEvent {
  SUBMIT,
  APPROVE,
  SEND,
  ACKNOWLEDGE,
  CANCEL
}
