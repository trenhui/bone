package com.bone.system.domain.model.config.vo;

import com.bone.core.exception.DomainException;

public record ConfigKey(String value) {
  public ConfigKey {
    if (value == null || value.isBlank()) {
      throw new DomainException("配置键不能为空");
    }
    if (value.length() > 255) {
      throw new DomainException("配置键长度不能超过255字符");
    }
  }

  public static ConfigKey of(String value) {
    return new ConfigKey(value);
  }
}
