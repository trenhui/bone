package com.bone.procurement.transaction.domain.model.po;

/** 采购订单领域事件（本期仅推进到 SEND，履约段事件保留枚举但无入口）。 */
public enum PoEvent {
  SUBMIT,
  APPROVE,
  SEND,
  CANCEL
}
