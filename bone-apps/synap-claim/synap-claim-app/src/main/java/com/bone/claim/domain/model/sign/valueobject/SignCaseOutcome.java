package com.bone.claim.domain.model.sign.valueobject;

/** 签收明细自动建案结果（写入明细 case_status 与轨迹的语义口径）。 */
public enum SignCaseOutcome {
  NEW,
  SKIPPED,
  AWAIT_MANUAL
}
