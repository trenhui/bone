package com.bone.claim.domain.model.mapping;

/** 映射状态。ACTIVE 与 {@code activeFlag='Y'} 必须同进同退（唯一键只看 activeFlag）。 */
public enum MappingStatus {
  DRAFT("DRAFT"),
  ACTIVE("ACTIVE"),
  DISABLED("DISABLED"),
  RETIRED("RETIRED");

  private final String code;

  MappingStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static MappingStatus fromCode(String code) {
    for (MappingStatus value : values()) {
      if (value.code.equals(code)) {
        return value;
      }
    }
    throw new IllegalArgumentException("未知映射状态: " + code);
  }
}
