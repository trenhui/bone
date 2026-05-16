package com.bone.metadata.engine.metadata;

/** 工作流未找到异常类 */
public class WorkflowNotFoundException extends RuntimeException {

  public WorkflowNotFoundException(String message) {
    super(message);
  }

  public WorkflowNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
