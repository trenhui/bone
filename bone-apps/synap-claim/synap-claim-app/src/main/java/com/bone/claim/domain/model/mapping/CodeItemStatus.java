package com.bone.claim.domain.model.mapping;

/** 保司码表项状态。{@code RETIRED} 表示保司已停用该码值，历史映射仍可解释。 */
public enum CodeItemStatus {
  ACTIVE("ACTIVE"),
  DISABLED("DISABLED"),
  RETIRED("RETIRED");

  private final String code;

  CodeItemStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static CodeItemStatus fromCode(String code) {
    for (CodeItemStatus value : values()) {
      if (value.code.equals(code)) {
        return value;
      }
    }
    throw new IllegalArgumentException("未知保司码表项状态: " + code);
  }
}
