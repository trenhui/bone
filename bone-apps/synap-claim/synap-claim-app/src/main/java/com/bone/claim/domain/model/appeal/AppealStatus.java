package com.bone.claim.domain.model.appeal;

/** 申诉状态（只追加 → 终态 DECIDED，§6.6 / §7.6）。 */
public enum AppealStatus {
  SUBMITTED,
  DECIDED
}
