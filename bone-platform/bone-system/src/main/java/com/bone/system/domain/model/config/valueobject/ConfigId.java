package com.bone.system.domain.model.config.valueobject;

import com.bone.core.exception.DomainException;
import java.util.UUID;

public record ConfigId(String value) {
  public ConfigId {
    if (value == null || value.isEmpty()) {
      throw new DomainException("配置ID不能为空");
    }
    try {
      UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new DomainException("配置ID必须是有效的UUID格式");
    }
  }

  public static ConfigId of(String value) {
    return new ConfigId(value);
  }
}
