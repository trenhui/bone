package com.bone.procurement.business.domain.model.business;

/** CAPA 状态：超期是转人工跟进态，不自动关闭、不自动放宽期限。 */
public enum CapaStatus {
  OPEN,
  OVERDUE,
  CLOSED
}
