package com.bone.claim.domain.model.claim.valueobject;

/** 业务阶段码（8 段大阶段，与 status 正交的第二维度，工程落地版 §6.1）。 */
public enum ClaimStage {
  INIT,
  SIGNING,
  PRE_EXAM,
  SUBMITTING,
  INSPECTION,
  AUDITING,
  REVIEWING,
  FINISH;

  /** DB name → 枚举（SDK String 持久化口径）。 */
  public static ClaimStage ofName(String name) {
    for (ClaimStage v : values()) {
      if (v.name().equals(name)) {
        return v;
      }
    }
    throw new IllegalArgumentException("未知ClaimStage: " + name);
  }
}
