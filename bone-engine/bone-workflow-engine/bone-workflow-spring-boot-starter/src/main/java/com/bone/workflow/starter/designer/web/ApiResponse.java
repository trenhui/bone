package com.bone.workflow.starter.designer.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 设计器 REST 响应信封：对齐骨核 {@code com.bone.core.model.ApiResponse} 与前端 {@code @bone/shared-types} 的
 * {@code ApiResponse<T> = { code, message, data }}。
 *
 * <p>前端 {@code createApiClient} 拦截器只在 HTTP 2xx 时解包 {@code .data}；4xx/5xx 走错误分支， 读取 {@code
 * response.data.message} 与 {@code response.data.data.errorCode} 作提示。故业务异常由 {@link
 * WorkflowDesignerExceptionAdvice} 统一转成「带 HTTP 状态码的 {@code ApiResponse}」——既让前端能按 HTTP 状态分支，也保留
 * {@code message} 给人看。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(int code, String message, T data) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(200, "ok", data);
  }

  public static <T> ApiResponse<T> of(int code, String message, T data) {
    return new ApiResponse<>(code, message, data);
  }
}
