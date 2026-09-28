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

  /**
   * 业务异常 → HTTP 状态 + {@code errorCode}。
   *
   * <p><b>为何不再「非 501 一律压成 400」</b>：{@link IntegrationErrors} 的「码 → 状态」表已给出 404 / 409 / 501
   * 等语义（错误码登记 §6 {@code INT_} 段），若此处仍只保留 501 一个分支，{@code INT_FLOW_NOT_FOUND} 会以 HTTP 400
   * 返回——前端按状态分流会把「不存在」误判成「参数非法」，与 bone-web / bone-system 的「HTTP 状态码与 {@code ApiResponse.code}
   * 对齐」契约相悖。故改为 400–599 直用、否则兜底 400。
   */
  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> bizException(BizException ex) {
    int httpStatus = toHttpStatus(ex.getCode());
    log.warn("Biz exception [{}] {}: {}", httpStatus, ex.getErrorCode(), ex.getMessage());
    String errorCode =
        ex.getErrorCode() != null ? ex.getErrorCode() : CommonErrorCodes.VALIDATION_FAILED;
    return ResponseEntity.status(httpStatus).body(problem(httpStatus, errorCode, ex.getMessage()));
  }

  /** 与 bone-web / bone-system 同一口径：{@code code} 是合法 HTTP 状态（400–599）则直用，否则兜底 400。 */
  private static int toHttpStatus(int code) {
    return (code >= 400 && code <= 599) ? code : HttpStatus.BAD_REQUEST.value();
  }

  private static ApiResponse<ProblemDetail> problem(int status, String errorCode, String message) {
    return ApiResponse.error(status, message, ProblemDetails.of(errorCode, status, message));
  }
}
