package com.bone.procurement.fulfillment.adapter.web;

import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import com.bone.procurement.fulfillment.domain.FulfillmentErrorCodes;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 履约异常映射：BizException 错误码原样回传；非法迁移 409 带 currentState/attempted/allowed。 */
@RestControllerAdvice(
    assignableTypes = {
      com.bone.procurement.fulfillment.adapter.web.controller.FulfillmentController.class
    })
public class FulfillmentExceptionHandler {

  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<?>> biz(BizException ex) {
    return ResponseEntity.status(ex.getCode())
        .body(
            ApiResponse.error(
                ex.getCode(), ex.getMessage(), Map.of("errorCode", ex.getErrorCode())));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<?>> illegalState(IllegalStateException ex) {
    String msg = ex.getMessage() == null ? "" : ex.getMessage();
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ApiResponse.error(
                409,
                msg,
                Map.of(
                    "errorCode",
                    FulfillmentErrorCodes.INVALID_TRANSITION,
                    "detail",
                    msg,
                    "allowed",
                    allowedOf(msg))));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<?>> illegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error(400, ex.getMessage() == null ? "" : ex.getMessage()));
  }

  private List<String> allowedOf(String msg) {
    int i = msg.indexOf("allowed=[");
    if (i < 0) return List.of();
    int end = msg.indexOf(']', i);
    if (end < 0) return List.of();
    String body = msg.substring(i + "allowed=[".length(), end).trim();
    if (body.isEmpty()) return List.of();
    return java.util.Arrays.stream(body.split(",")).map(String::trim).toList();
  }
}
