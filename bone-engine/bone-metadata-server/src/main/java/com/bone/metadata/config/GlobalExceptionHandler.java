package com.bone.metadata.config;

import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.metadata.catalog.common.CatalogApiResponses;
import com.bone.metadata.catalog.common.MetaErrorCodes;
import com.bone.metadata.catalog.common.exception.CatalogIdempotencyConflictException;
import com.bone.metadata.catalog.common.exception.CatalogOptimisticLockException;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.exception.FieldConflictException;
import com.bone.metadata.exception.TooManyRequestsException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 全局异常 → HTTP 状态 + {@link ApiResponse}{@code <ProblemDetail>}（对齐 Bone-API §4）。 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleValidation(
      MethodArgumentNotValidException ex) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining(", "));
    return CatalogApiResponses.problem(
        HttpStatus.BAD_REQUEST, MetaErrorCodes.VALIDATION_FAILED, detail);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleMessageNotReadable(
      HttpMessageNotReadableException ex) {
    String detail = ex.getMostSpecificCause().getMessage();
    return CatalogApiResponses.problem(
        HttpStatus.BAD_REQUEST, MetaErrorCodes.VALIDATION_FAILED, detail);
  }

  @ExceptionHandler(RuntimeRecordException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleRuntimeRecord(RuntimeRecordException ex) {
    HttpStatus status =
        switch (ex.getErrorCode()) {
          case "META_RUNTIME_RECORD_NOT_FOUND" -> HttpStatus.NOT_FOUND;
          case "META_PRECONDITION_FAILED" -> HttpStatus.PRECONDITION_FAILED;
          case "META_RUNTIME_INVALID_QUERY", "META_RUNTIME_INVALID_IDENTIFIER" -> HttpStatus
              .BAD_REQUEST;
          default -> HttpStatus.BAD_REQUEST;
        };
    return CatalogApiResponses.problem(status, ex.getErrorCode(), ex.getMessage());
  }

  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleBiz(BizException ex) {
    return CatalogApiResponses.problem(
        HttpStatus.BAD_REQUEST, MetaErrorCodes.BIZ_ERROR, ex.getMessage());
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleDomain(DomainException ex) {
    return CatalogApiResponses.problem(
        HttpStatus.CONFLICT, MetaErrorCodes.DOMAIN_ERROR, ex.getMessage());
  }

  @ExceptionHandler(CatalogOptimisticLockException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleOptimisticLock(
      CatalogOptimisticLockException ex) {
    return CatalogApiResponses.problem(
        HttpStatus.PRECONDITION_FAILED, MetaErrorCodes.PRECONDITION_FAILED, ex.getMessage());
  }

  @ExceptionHandler(CatalogIdempotencyConflictException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleIdempotencyConflict(
      CatalogIdempotencyConflictException ex) {
    return CatalogApiResponses.problem(
        HttpStatus.CONFLICT, MetaErrorCodes.IDEMPOTENCY_CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(FieldConflictException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleFieldConflict(FieldConflictException ex) {
    return CatalogApiResponses.problem(
        HttpStatus.CONFLICT, MetaErrorCodes.FIELD_CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(TooManyRequestsException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleTooManyRequests(
      TooManyRequestsException ex) {
    return CatalogApiResponses.problem(
        HttpStatus.TOO_MANY_REQUESTS, MetaErrorCodes.TOO_MANY_REQUESTS, ex.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleAccessDenied(AccessDeniedException ex) {
    return CatalogApiResponses.problem(HttpStatus.FORBIDDEN, "META_FORBIDDEN", "无权限访问");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleGeneral(Exception ex) {
    log.error("未处理异常", ex);
    return CatalogApiResponses.problem(
        HttpStatus.INTERNAL_SERVER_ERROR, MetaErrorCodes.INTERNAL_ERROR, "服务器内部错误，请稍后重试");
  }

  /**
   * 请求地址不存在 → 404（而非被 catch-all 兜底成 500）。
   *
   * <p>Spring 6（Boot 3）下未匹配路由抛出 {@link NoResourceFoundException}，必须显式处理，否则会被
   * {@code @ExceptionHandler(Exception.class)} 兜底成 500，使「接口不存在」与「服务器内部错误」语义混淆。
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleNoResourceFound(
      NoResourceFoundException ex) {
    log.warn("No resource found: {}", ex.getResourcePath());
    return CatalogApiResponses.problem(
        HttpStatus.NOT_FOUND, MetaErrorCodes.NOT_FOUND, "请求地址不存在: " + ex.getResourcePath());
  }
}
