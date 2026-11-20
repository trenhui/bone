package com.bone.procurement.contract.domain.model.review;

/** 风险等级：UNKNOWN 是独立风险态，不得被压成 LOW / 无风险。 */
public enum RiskLevel {
  LOW,
  MEDIUM,
  HIGH,
  UNKNOWN
}
