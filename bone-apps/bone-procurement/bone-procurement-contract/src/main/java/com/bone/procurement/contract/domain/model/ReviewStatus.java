package com.bone.procurement.contract.domain.model;

/** 审查聚合状态机状态。 */
public enum ReviewStatus {
  PENDING,
  PARSED,
  IN_REVIEW,
  HUMAN_REVIEW,
  SNAPSHOTTED,
  FAILED
}
