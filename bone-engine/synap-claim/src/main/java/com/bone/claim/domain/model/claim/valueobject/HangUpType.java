package com.bone.claim.domain.model.claim.valueobject;

/** 挂起类型（正交机制，不改状态码；工程落地版 §6.5）。 */
public enum HangUpType {
  /** 缺件挂起。 */
  EVIDENCE,
  /** 调查挂起（风控命中/金额异常）。 */
  INVESTIGATION,
  /** 超时挂起（阶段 SLA 超时）。 */
  TIMEOUT,
  /** 疑似欺诈挂起（仅风控岗有权恢复，不设自动恢复）。 */
  FRAUD;

  /** DB name → 枚举（SDK String 持久化口径）。 */
  public static HangUpType ofName(String name) {
    for (HangUpType v : values()) {
      if (v.name().equals(name)) {
        return v;
      }
    }
    throw new IllegalArgumentException("未知HangUpType: " + name);
  }
}
