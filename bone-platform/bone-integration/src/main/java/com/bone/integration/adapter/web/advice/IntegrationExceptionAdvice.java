package com.bone.integration.adapter.web.advice;

import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.model.ApiResponse;
import com.bone.integration.common.exception.NotFoundException;
import com.bone.integration.common.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 集成模块异常映射：未实现连接器返回 HTTP 501，避免假成功（INT-01）。 */
@Slf4j
@RestControllerAdvice
public class IntegrationExceptionAdvice {

  private static final int NOT_IMPLEMENTED_CODE = 501;

  @ExceptionHandler(UnsupportedOperationException.class)
  public ResponseEntity<ApiResponse<Void>> notImplemented(UnsupportedOperationException ex) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .body(
            ApiResponse.error(
                NOT_IMPLEMENTED_CODE, "INT_CONNECTOR_NOT_IMPLEMENTED: " + ex.getMessage()));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> notFound(NotFoundException ex) {
    log.warn("Not found: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(404, ex.getMessage()));
  }

  @ExceptionHandler(SystemException.class)
  public ResponseEntity<ApiResponse<Void>> systemException(SystemException ex) {
    log.error("System exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(500, ex.getMessage()));
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiResponse<Void>> domainException(DomainException ex) {
    log.warn("Domain exception: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(400, ex.getMessage()));
  }

  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<Void>> bizException(BizException ex) {
    HttpStatus status =
        ex.getCode() == NOT_IMPLEMENTED_CODE ? HttpStatus.NOT_IMPLEMENTED : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
  }
}
