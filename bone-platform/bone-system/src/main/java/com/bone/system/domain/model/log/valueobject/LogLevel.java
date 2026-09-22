package com.bone.system.domain.model.log.valueobject;

import com.bone.core.exception.DomainException;

public enum LogLevel {
  ERROR,
  WARN,
  INFO,
  DEBUG,
  TRACE;

  public static LogLevel fromString(String value) {
    if (value == null || value.isBlank()) {
      throw new DomainException("日志级别不能为空");
    }
    for (LogLevel level : values()) {
      if (level.name().equalsIgnoreCase(value)) {
        return level;
      }
    }
    throw new DomainException("无效的日志级别: " + value);
  }
}
