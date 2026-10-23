package com.bone.workflow.domain;

/**
 * 审批流引擎异常：携带稳定错误分类，供宿主映射为平台错误码。
 *
 * <p>命名刻意<b>不</b>以 {@code BusinessException} 结尾——平台架构守护禁止业务/引擎模块自建该后缀的异常类 （统一走 {@code
 * com.bone.core.exception.BizException}）；而本引擎刻意<b>不依赖平台</b>（对 {@code bone-metadata-engine}
 * 零依赖，才能被任意宿主复用），故自带一个不依赖平台的异常类型。
 *
 * <p>宿主用法：{@code catch (WorkflowEngineException e) { throw BizException.of(..., map(e.code()),
 * e.getMessage()); }} ——按 {@link #code()} 映射，而不是解析 {@link #getMessage()}。
 */
public class WorkflowEngineException extends RuntimeException {

  private final WorkflowErrorCode code;

  public WorkflowEngineException(WorkflowErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public WorkflowEngineException(WorkflowErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  /** 稳定错误分类。 */
  public WorkflowErrorCode code() {
    return code;
  }
}
