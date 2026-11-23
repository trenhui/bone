package com.bone.procurement.sourcing.adapter.web;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.sourcing.adapter.web.controller.SourcingController;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 寻源异常映射：冒名/匿名/非法参数按 400 拒绝，终态冲突按 409 拒绝。 */
@RestControllerAdvice(assignableTypes = SourcingController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SourcingExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<?>> illegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest()
        .body(
            ApiResponse.error(
                400,
                ex.getMessage() == null ? "" : ex.getMessage(),
                Map.of("errorCode", "INVALID_SOURCING_REQUEST")));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<?>> illegalState(IllegalStateException ex) {
    String message = ex.getMessage() == null ? "" : ex.getMessage();
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error(409, message, Map.of("errorCode", "SOURCING_CONFLICT")));
  }
}
