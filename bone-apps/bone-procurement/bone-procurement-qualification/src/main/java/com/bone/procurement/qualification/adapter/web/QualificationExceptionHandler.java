package com.bone.procurement.qualification.adapter.web;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.qualification.adapter.web.controller.QualificationController;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 准入异常映射：身份/租户冒名与非法参数按 400 拒绝，状态冲突按 409 拒绝。 */
@RestControllerAdvice(assignableTypes = QualificationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QualificationExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<?>> illegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest()
        .body(
            ApiResponse.error(
                400,
                ex.getMessage() == null ? "" : ex.getMessage(),
                Map.of("errorCode", "INVALID_QUALIFICATION_REQUEST")));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<?>> illegalState(IllegalStateException ex) {
    String message = ex.getMessage() == null ? "" : ex.getMessage();
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error(409, message, Map.of("errorCode", "QUALIFICATION_CONFLICT")));
  }
}
