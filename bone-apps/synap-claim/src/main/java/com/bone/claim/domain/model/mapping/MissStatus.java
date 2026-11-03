package com.bone.claim.domain.model.mapping;

/** 未命中队列状态。 */
public enum MissStatus {
  OPEN("OPEN"),
  RESOLVED("RESOLVED"),
  IGNORED("IGNORED");

  private final String code;

  MissStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static MissStatus fromCode(String code) {
    for (MissStatus value : values()) {
      if (value.code.equals(code)) {
        return value;
      }
    }
    throw new IllegalArgumentException("未知未命中状态: " + code);
  }
}
