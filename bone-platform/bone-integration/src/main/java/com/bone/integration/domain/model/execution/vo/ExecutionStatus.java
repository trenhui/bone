package com.bone.integration.domain.model.execution.vo;

public enum ExecutionStatus {
  PENDING,
  RUNNING,
  SUCCESS,
  FAILED,
  TIMEOUT;

  public boolean isPending() {
    return this == PENDING;
  }

  public boolean isRunning() {
    return this == RUNNING;
  }

  public boolean isSuccess() {
    return this == SUCCESS;
  }

  public boolean isFailed() {
    return this == FAILED;
  }

  public boolean isTimeout() {
    return this == TIMEOUT;
  }
}
