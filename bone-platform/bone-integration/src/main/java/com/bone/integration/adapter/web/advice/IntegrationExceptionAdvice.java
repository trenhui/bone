package com.bone.integration.adapter.web.advice;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.core.model.ProblemDetails;
import com.bone.integration.common.exception.NotFoundException;
import com.bone.integration.common.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 集成模块异常映射：未实现连接器返回 HTTP 501，避免假成功（INT-01）。
 *
 * <p><b>i18n 改造（方案 §6.2 / §12.3）</b>：此前全部返回 {@code ApiResponse<Void>}（{@code data} 为 {@code null}），
 * 前端拿不到 {@code errorCode}；「未实现」这类语义还是靠 {@code "INT_CONNECTOR_NOT_IMPLEMENTED: "} 前缀拼在 message
 * 里（文案一改，聚合口径就断）。现统一产出 {@link ProblemDetail}：语义落到 {@code errorCode}， message 只作中文 fallback。
 */
@Slf4j
@RestControllerAdvice
public class IntegrationExceptionAdvice {

  private static final int NOT_IMPLEMENTED_CODE = 501;

  /** 未实现 → 501。码用 {@code COMMON_NOT_IMPLEMENTED}：不再是 message 前缀，而是可聚合的稳定码。 */
  @ExceptionHandler(UnsupportedOperationException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> notImplemented(
      UnsupportedOperationException ex) {
    String message = "连接器能力未实现: " + ex.getMessage();
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .body(problem(NOT_IMPLEMENTED_CODE, CommonErrorCodes.NOT_IMPLEMENTED, message));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> notFound(NotFoundException ex) {
    log.warn("Not found: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(problem(404, CommonErrorCodes.NOT_FOUND, ex.getMessage()));
  }

  @ExceptionHandler(SystemException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> systemException(SystemException ex) {
    log.error("System exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(problem(500, CommonErrorCodes.INTERNAL_ERROR, ex.getMessage()));
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> domainException(DomainException ex) {
    log.warn("Domain exception: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(problem(400, CommonErrorCodes.VALIDATION_FAILED, ex.getMessage()));
  }

  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> bizException(BizException ex) {
    HttpStatus status =
        ex.getCode() == NOT_IMPLEMENTED_CODE ? HttpStatus.NOT_IMPLEMENTED : HttpStatus.BAD_REQUEST;
    int httpStatus = status.value();
    String errorCode =
        ex.getErrorCode() != null
            ? ex.getErrorCode()
            : (httpStatus == NOT_IMPLEMENTED_CODE
                ? CommonErrorCodes.NOT_IMPLEMENTED
                : CommonErrorCodes.VALIDATION_FAILED);
    return ResponseEntity.status(status).body(problem(httpStatus, errorCode, ex.getMessage()));
  }

  private static ApiResponse<ProblemDetail> problem(int status, String errorCode, String message) {
    return ApiResponse.error(status, message, ProblemDetails.of(errorCode, status, message));
  }
}
