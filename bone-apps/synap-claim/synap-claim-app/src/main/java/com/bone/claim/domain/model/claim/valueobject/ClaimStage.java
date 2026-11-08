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

  /**
   * 该阶段的内部作业 SLA 目标工时（§6.6 内部作业 SLA 表，工作日小时口径）。
   *
   * <p>SIGNING ≤4h / PRE_EXAM ≤4h / SUBMITTING ≤72h / AUDITING ≤48h / REVIEWING ≤8h / FINISH
   * T+1（24h）。 INSPECTION（后督抽检，V2 启用）暂按 8h；INIT 为 0。供 SLA 物化计算 {@code remaining_work_hours} 的原始工时基线。
   */
  public java.math.BigDecimal baseWorkHours() {
    return switch (this) {
      case SIGNING -> new java.math.BigDecimal("4.00");
      case PRE_EXAM -> new java.math.BigDecimal("4.00");
      case SUBMITTING -> new java.math.BigDecimal("72.00");
      case INSPECTION -> new java.math.BigDecimal("8.00");
      case AUDITING -> new java.math.BigDecimal("48.00");
      case REVIEWING -> new java.math.BigDecimal("8.00");
      case FINISH -> new java.math.BigDecimal("24.00");
      case INIT -> new java.math.BigDecimal("0.00");
    };
  }
}
