package com.bone.system.infrastructure.config;

import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.model.ApiResponse;
import com.bone.system.common.exception.NotFoundException;
import com.bone.system.common.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(DomainException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleDomainException(DomainException e) {
    log.warn("Domain exception: {}", e.getMessage());
    return ApiResponse.error(400, e.getMessage());
  }

  @ExceptionHandler(BizException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleBizException(BizException e) {
    log.warn("Biz exception [{}]: {}", e.getCode(), e.getMessage());
    return ApiResponse.error(e.getCode() > 0 ? e.getCode() : 400, e.getMessage());
  }

  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> handleNotFoundException(NotFoundException e) {
    log.warn("Not found exception: {}", e.getMessage());
    return ApiResponse.error(404, e.getMessage());
  }

  @ExceptionHandler(SystemException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<Void> handleSystemException(SystemException e) {
    log.error("System exception", e);
    return ApiResponse.error(500, e.getMessage());
  }

  /**
   * 方法级 {@code @PreAuthorize} 失败 → 403。必须显式处理， 否则会被下方 {@code @ExceptionHandler(Exception.class)}
   * 兜底为 500。
   */
  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiResponse<Void> handleAccessDeniedException(AccessDeniedException e) {
    log.warn("Access denied: {}", e.getMessage());
    return ApiResponse.error(403, "Forbidden");
  }

  /** 认证失败 → 401（Spring Security 通常在 filter 层就处理，留兜底）。 */
  @ExceptionHandler(AuthenticationException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiResponse<Void> handleAuthenticationException(AuthenticationException e) {
    log.warn("Authentication failed: {}", e.getMessage());
    return ApiResponse.error(401, "Unauthorized");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
    return ApiResponse.error(400, message);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<Void> handleException(Exception e) {
    log.error("Unexpected exception", e);
    return ApiResponse.error(500, "Internal server error");
  }
}
