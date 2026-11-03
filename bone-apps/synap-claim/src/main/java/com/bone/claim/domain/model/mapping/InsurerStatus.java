package com.bone.claim.domain.model.mapping;

/** 承保主体生命周期状态。{@code TERMINATED} 表示终止合作，不是删除记录。 */
public enum InsurerStatus {
  ACTIVE("ACTIVE"),
  DISABLED("DISABLED"),
  TERMINATED("TERMINATED");

  private final String code;

  InsurerStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static InsurerStatus fromCode(String code) {
    for (InsurerStatus value : values()) {
      if (value.code.equals(code)) {
        return value;
      }
    }
    throw new IllegalArgumentException("未知承保主体状态: " + code);
  }
}
