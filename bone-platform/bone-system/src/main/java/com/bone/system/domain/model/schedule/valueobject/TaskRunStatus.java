package com.bone.system.domain.model.schedule.valueobject;

import com.bone.core.exception.DomainException;

/**
 * 定时任务单次执行结果。
 *
 * <p>刻意只有两态：执行记录要回答的是「这次跑成了没有」；进行中（RUNNING）不落行—— 记录在 {@code finished_at}
 * 写入时才是终态，半行数据只会让「失败了多少次」的数不准。
 */
public enum TaskRunStatus {
  SUCCESS,
  FAILED;

  public static TaskRunStatus fromString(String value) {
    if (value == null) {
      throw new DomainException("执行结果不能为空");
    }
    for (TaskRunStatus s : values()) {
      if (s.name().equalsIgnoreCase(value)) {
        return s;
      }
    }
    throw new DomainException("未知执行结果: " + value);
  }
}
