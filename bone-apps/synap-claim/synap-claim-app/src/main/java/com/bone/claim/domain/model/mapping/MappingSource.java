package com.bone.claim.domain.model.mapping;

/** 映射来源：人工 / 批量导入 / 采纳机器建议。 */
public enum MappingSource {
  MANUAL("MANUAL"),
  IMPORT("IMPORT"),
  SUGGEST_CONFIRMED("SUGGEST_CONFIRMED");

  private final String code;

  MappingSource(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static MappingSource fromCode(String code) {
    for (MappingSource value : values()) {
      if (value.code.equals(code)) {
        return value;
      }
    }
    throw new IllegalArgumentException("未知映射来源: " + code);
  }
}
