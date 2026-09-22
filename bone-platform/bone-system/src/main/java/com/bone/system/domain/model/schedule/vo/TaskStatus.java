package com.bone.system.domain.model.schedule.vo;

import com.bone.core.exception.BizException;

/** 定时任务状态。 */
public enum TaskStatus {
  ENABLED,
  DISABLED;

  public static TaskStatus fromString(String value) {
    if (value == null) {
      return DISABLED;
    }
    for (TaskStatus s : values()) {
      if (s.name().equalsIgnoreCase(value)) {
        return s;
      }
    }
    throw BizException.of("未知任务状态: " + value);
  }
}
