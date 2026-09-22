package com.bone.system.domain.model.config.valueobject;

import com.bone.core.exception.DomainException;

public record ConfigValue(String value) {
  public ConfigValue {
    if (value == null) {
      throw new DomainException("配置值不能为空");
    }
  }

  public static ConfigValue of(String value) {
    return new ConfigValue(value);
  }
}
