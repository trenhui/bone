package com.bone.system.domain.alert.vo;

public enum AlertStatus {
  TRIGGERED("触发"),
  RESOLVED("解决");

  private final String description;

  AlertStatus(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  /** 按枚举名（不区分大小写）解析；非法值抛 {@link IllegalArgumentException}，由应用层翻译成 400。 */
  public static AlertStatus fromString(String value) {
    for (AlertStatus status : values()) {
      if (status.name().equalsIgnoreCase(value)) {
        return status;
      }
    }
    throw new IllegalArgumentException("unknown alert status: " + value);
  }
}
