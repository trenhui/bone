package com.bone.engine.extension.studio.config;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.engine.extension.studio.application.support.StudioCommandResponses;
import com.bone.engine.extension.studio.common.StudioErrorCodes;
import com.bone.engine.extension.studio.common.exception.IdempotencyConflictException;
import com.bone.engine.extension.studio.common.exception.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Studio API 统一异常 → {@link ApiResponse} + {@link ProblemDetail}。 */
@RestControllerAdvice(basePackages = "com.bone.engine.extension.studio.adapter.web.controller")
public class StudioWebExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(StudioWebExceptionHandler.class);

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> forbidden(AccessDeniedException ex) {
    return StudioCommandResponses.problem(
        HttpStatus.FORBIDDEN, StudioErrorCodes.FORBIDDEN, "无权限访问");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> badRequest(IllegalArgumentException ex) {
    return problem(HttpStatus.BAD_REQUEST, StudioErrorCodes.VALIDATION_FAILED, ex.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> conflict(IllegalStateException ex) {
    return problem(HttpStatus.CONFLICT, StudioErrorCodes.STATE_INVALID, ex.getMessage());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> idempotencyConflict(
      IdempotencyConflictException ex) {
    return StudioCommandResponses.problem(
        HttpStatus.CONFLICT, StudioErrorCodes.IDEMPOTENCY_CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(OptimisticLockException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> optimisticLock(OptimisticLockException ex) {
    return StudioCommandResponses.problem(
        HttpStatus.PRECONDITION_FAILED, StudioErrorCodes.PRECONDITION_FAILED, ex.getMessage());
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> missingRequestPart(
      MissingServletRequestPartException ex) {
    return problem(HttpStatus.BAD_REQUEST, StudioErrorCodes.VALIDATION_FAILED, ex.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> internal(Exception ex) {
    log.error("[API] unhandled traceId={}", MDC.get(StudioRequestContextFilter.TRACE_ID), ex);
    return StudioCommandResponses.problem(
        HttpStatus.INTERNAL_SERVER_ERROR, StudioErrorCodes.INTERNAL_ERROR, "服务内部错误");
  }

  private static ResponseEntity<ApiResponse<ProblemDetail>> problem(
      HttpStatus status, String errorCode, String detail) {
    return StudioCommandResponses.problem(status, errorCode, detail);
  }
}
