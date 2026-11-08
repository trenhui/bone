package com.bone.claim.domain.model.mapping;

/** 别名关系：只允许 1:1 的两种；拆分（1:N）不在本枚举内。 */
public enum AliasRelation {
  MERGE("MERGE"),
  RENAME("RENAME");

  private final String code;

  AliasRelation(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static AliasRelation fromCode(String code) {
    for (AliasRelation value : values()) {
      if (value.code.equals(code)) {
        return value;
      }
    }
    throw new IllegalArgumentException("未知别名关系: " + code);
  }
}
