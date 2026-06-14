package com.bone.integration.domain.model.execution.vo;

import com.bone.core.exception.DomainException;

public record ExecutionLogId(Long value) {
  public ExecutionLogId {
    if (value == null || value <= 0) {
      throw new DomainException("执行日志ID必须大于0");
    }
  }

  public static ExecutionLogId of(Long value) {
    return new ExecutionLogId(value);
  }
}
