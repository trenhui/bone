package com.bone.procurement.transaction.domain.model.requisition;

/** 请购单领域事件。 */
public enum RequisitionEvent {
  SUBMIT,
  APPROVE,
  REJECT,
  CONVERT,
  CANCEL
}
