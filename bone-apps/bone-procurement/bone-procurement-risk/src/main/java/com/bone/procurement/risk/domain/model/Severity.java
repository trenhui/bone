package com.bone.procurement.risk.domain.model;

/** 风险严重度：UNKNOWN 为不确定态，必须 fail-closed 转人工，不得当低风险放行。 */
public enum Severity {
  HIGH,
  MEDIUM,
  LOW,
  UNKNOWN;

  public boolean isIndeterminate() {
    return this == UNKNOWN;
  }

  public static Severity parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return UNKNOWN;
    }
    try {
      return valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return UNKNOWN;
    }
  }
}
