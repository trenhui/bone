package com.bone.system.domain.model.config.vo;

import com.bone.core.exception.DomainException;

public enum ConfigType {
  SYSTEM("系统级"),
  SERVICE("服务级"),
  FEATURE("功能级");

  private final String description;

  ConfigType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static ConfigType fromString(String value) {
    for (ConfigType type : values()) {
      if (type.name().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new DomainException("无效的配置类型: " + value);
  }
}
