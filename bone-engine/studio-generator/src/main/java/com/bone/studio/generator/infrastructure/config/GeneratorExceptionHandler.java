package com.bone.studio.generator.infrastructure.config;

import com.bone.core.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * studio-generator 全局异常处理器。
 *
 * <p>将 IllegalArgumentException / NullPointerException 等转为符合 API 规范的 HTTP 响应， 避免未捕获异常直接返回 500。
 */
@Slf4j
@RestControllerAdvice
public class GeneratorExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<?>> handleBadRequest(IllegalArgumentException ex) {
    log.warn("[handleBadRequest] {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(400, ex.getMessage()));
  }

  @ExceptionHandler(NullPointerException.class)
  public ResponseEntity<ApiResponse<?>> handleNpe(NullPointerException ex) {
    log.error("[handleNpe] 空指针异常", ex);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(400, "请求参数不完整: " + ex.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<?>> handleIllegalState(IllegalStateException ex) {
    log.warn("[handleIllegalState] {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(409, ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<?>> handleException(Exception ex) {
    log.error("[handleException] 未预期异常", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(500, "服务内部错误"));
  }
}
