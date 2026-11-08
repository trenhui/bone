package com.bone.claim.domain.model.mapping;

/** 映射语义类型：精确同码 / 别名同义 / 归并分组（多对一）。 */
public enum MappingType {
  EXACT("EXACT"),
  SYNONYM("SYNONYM"),
  GROUP("GROUP");

  private final String code;

  MappingType(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static MappingType fromCode(String code) {
    for (MappingType value : values()) {
      if (value.code.equals(code)) {
        return value;
      }
    }
    throw new IllegalArgumentException("未知映射类型: " + code);
  }
}
