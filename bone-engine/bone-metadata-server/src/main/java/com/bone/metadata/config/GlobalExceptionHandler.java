package com.bone.metadata.config;

import com.bone.core.util.JsonUtil;
import com.bone.metadata.exception.ErrorResponse;
import com.bone.metadata.exception.FieldConflictException;
import com.bone.metadata.exception.TooManyRequestsException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

/** 全局异常处理器，统一返回 ProblemDetail 格式或自定义错误响应 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException ex) {
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.toList());

    ErrorResponse resp = new ErrorResponse("VALIDATION_FAILED", String.join(", ", errors), ex);
    return ResponseEntity.badRequest().body(resp);
  }

  @ExceptionHandler(FieldConflictException.class)
  public ResponseEntity<ErrorResponse> handleFieldConflict(FieldConflictException ex) {
    ErrorResponse resp = new ErrorResponse("FIELD_CONFLICT", ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(resp);
  }

  @ExceptionHandler(TooManyRequestsException.class)
  public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
    ErrorResponse resp = new ErrorResponse("TOO_MANY_REQUESTS", ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(resp);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneralError(Exception ex, WebRequest request) {
    ErrorResponse resp = new ErrorResponse("INTERNAL_ERROR", "服务器内部错误，请稍后重试", ex);
    Map<String, String> requestInfo = new HashMap<>();
    requestInfo.put("uri", request.getContextPath());
    requestInfo.put("user", request.getRemoteUser());
    log.error("INTERNAL_ERROR:" + JsonUtil.toJson(requestInfo), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
  }
}
