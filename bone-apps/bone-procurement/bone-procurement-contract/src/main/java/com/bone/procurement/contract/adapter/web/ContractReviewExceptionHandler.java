package com.bone.procurement.contract.adapter.web;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.contract.adapter.web.controller.ContractReviewController;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 合同审查异常映射：自报身份/空输入按 400 拒绝，状态闸门冲突按 409 拒绝，避免 fail-closed 退化成 500。 */
@RestControllerAdvice(assignableTypes = ContractReviewController.class)
public class ContractReviewExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<?>> illegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest()
        .body(
            ApiResponse.error(
                400,
                ex.getMessage() == null ? "" : ex.getMessage(),
                Map.of("errorCode", "INVALID_CONTRACT_REVIEW_REQUEST")));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<?>> illegalState(IllegalStateException ex) {
    String message = ex.getMessage() == null ? "" : ex.getMessage();
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error(409, message, Map.of("errorCode", "CONTRACT_REVIEW_CONFLICT")));
  }
}
