package com.bone.system.domain.model.alert.valueobject;

import com.bone.core.exception.DomainException;

public enum AlertLevel {
  CRITICAL("严重"),
  WARNING("警告"),
  INFO("信息");

  private final String description;

  AlertLevel(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static AlertLevel fromString(String value) {
    for (AlertLevel level : values()) {
      if (level.name().equalsIgnoreCase(value)) {
        return level;
      }
    }
    throw new DomainException("无效的告警级别: " + value);
  }
}
