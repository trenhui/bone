package com.bone.system.domain.model.schedule.valueobject;

import com.bone.core.exception.DomainException;

/** 触发方式：CRON=调度器按表达式触发；MANUAL=用户点「立即执行」。 */
public enum TriggerType {
  CRON,
  MANUAL;

  public static TriggerType fromString(String value) {
    if (value == null) {
      return CRON;
    }
    for (TriggerType t : values()) {
      if (t.name().equalsIgnoreCase(value)) {
        return t;
      }
    }
    throw new DomainException("未知触发方式: " + value);
  }
}
