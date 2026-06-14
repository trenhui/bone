package com.bone.masterdata.domain.model.quality.vo;

public enum RuleSeverity {
  LOW(1, "低"),
  MEDIUM(2, "中"),
  HIGH(3, "高"),
  CRITICAL(4, "严重");

  private final int code;
  private final String description;

  RuleSeverity(int code, String description) {
    this.code = code;
    this.description = description;
  }

  /** 供 metadata-sdk {@code SqlUtil.toJdbcParameter} 反射调用，映射到 TINYINT 列 */
  public int getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  public static RuleSeverity fromCode(int code) {
    return of(code);
  }

  /** 供 metadata-sdk {@code SmartRowMapper} 反射调用，将 TINYINT 列值还原为枚举 */
  public static RuleSeverity of(int code) {
    for (RuleSeverity severity : values()) {
      if (severity.code == code) {
        return severity;
      }
    }
    throw new IllegalArgumentException("未知的严重级别: " + code);
  }
}
