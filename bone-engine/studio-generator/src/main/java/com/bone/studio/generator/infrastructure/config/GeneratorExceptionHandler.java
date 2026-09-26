package com.bone.studio.generator.infrastructure.config;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.exception.NotFoundException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.core.model.ProblemDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * studio-generator 全局异常处理器。
 *
 * <p>将 IllegalArgumentException / NullPointerException 等转为符合 API 规范的 HTTP 响应， 避免未捕获异常直接返回 500。
 *
 * <p><b>i18n 改造（方案 §6.2 / §12.3）</b>：此前全部返回 {@code ApiResponse.error(code, "中文")}（{@code data} 为
 * {@code null}），前端 {@code showError()} 的 errorCode 分支在本模块不命中。现统一产出 {@link ProblemDetail}， 基础设施语义复用
 * {@link CommonErrorCodes}，{@code message} 保持中文（fallback 契约）。
 */
@Slf4j
@RestControllerAdvice
public class GeneratorExceptionHandler {

  /** 放行 Spring 内置的状态码异常（404/304 等），保留其 HTTP 状态，避免被兜底处理器转成 500。 */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleResponseStatus(
      ResponseStatusException ex) {
    log.warn("[handleResponseStatus] {} {}", ex.getStatusCode(), ex.getReason());
    String message = ex.getReason() != null ? ex.getReason() : "请求处理失败";
    int status = ex.getStatusCode().value();
    return ResponseEntity.status(ex.getStatusCode())
        .body(problem(status, errorCodeOfStatus(status), message));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleBadRequest(IllegalArgumentException ex) {
    log.warn("[handleBadRequest] {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(problem(400, CommonErrorCodes.VALIDATION_FAILED, ex.getMessage()));
  }

  @ExceptionHandler(NullPointerException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleNpe(NullPointerException ex) {
    log.error("[handleNpe] 空指针异常", ex);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(problem(400, CommonErrorCodes.MALFORMED_REQUEST, "请求参数不完整: " + ex.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleIllegalState(IllegalStateException ex) {
    log.warn("[handleIllegalState] {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(problem(409, CommonErrorCodes.CONFLICT, ex.getMessage()));
  }

  /** 业务异常：按异常自带的 code 翻译为对应 HTTP 状态（如 BizException(404) → 404，而非 500）。 */
  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleBizException(BizException ex) {
    int code = ex.getCode();
    log.warn("[handleBizException] code={} {}", code, ex.getMessage());
    int status = toHttpStatus(code);
    return ResponseEntity.status(status).body(problem(status, ex.getErrorCode(), ex.getMessage()));
  }

  /** 领域校验异常 → 400。 */
  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleDomainException(DomainException ex) {
    log.warn("[handleDomainException] {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(problem(400, CommonErrorCodes.VALIDATION_FAILED, ex.getMessage()));
  }

  /** 资源不存在 → 404（如模板不存在）。 */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleNotFoundException(NotFoundException ex) {
    log.warn("[handleNotFoundException] {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(problem(404, CommonErrorCodes.NOT_FOUND, ex.getMessage()));
  }

  /**
   * 请求方法不被支持 → 405（而非被 catch-all 兜底成 500）。
   *
   * <p>本类的 {@code @ExceptionHandler(Exception.class)} 兜底会抢在 Spring 默认 405 解析之前命中，
   * 必须显式声明，否则「客户端用错方法」会被伪装成服务端 500。
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex) {
    log.warn("[handleMethodNotSupported] {}", ex.getMethod());
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(problem(405, CommonErrorCodes.METHOD_NOT_ALLOWED, "请求方法不被支持: " + ex.getMethod()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleException(Exception ex) {
    log.error("[handleException] 未预期异常", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(problem(500, CommonErrorCodes.INTERNAL_ERROR, "服务内部错误"));
  }

  /**
   * 请求地址不存在 → 404（而非被 catch-all 兜底成 500）。
   *
   * <p>Spring 6（Boot 3）下未匹配路由抛出 {@link NoResourceFoundException}；必须显式处理，否则会被
   * {@code @ExceptionHandler(Exception.class)} 兜底成 500，使「接口不存在」与「服务器内部错误」语义混淆。
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleNoResourceFound(
      NoResourceFoundException ex) {
    log.warn("[handleNoResourceFound] {}", ex.getResourcePath());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(problem(404, CommonErrorCodes.NOT_FOUND, "请求地址不存在: " + ex.getResourcePath()));
  }

  /** 将业务错误码映射为 HTTP 状态码：code 本身合法（400–599）则直接用，否则默认 400。 */
  private static int toHttpStatus(int code) {
    return (code >= 400 && code <= 599) ? code : 400;
  }

  /** ResponseStatusException 无业务码，按状态归入最近的公共码（避免前端拿到 null 而降级为中文）。 */
  private static String errorCodeOfStatus(int status) {
    return switch (status) {
      case 404 -> CommonErrorCodes.NOT_FOUND;
      case 405 -> CommonErrorCodes.METHOD_NOT_ALLOWED;
      case 409 -> CommonErrorCodes.CONFLICT;
      case 401 -> CommonErrorCodes.UNAUTHORIZED;
      case 403 -> CommonErrorCodes.FORBIDDEN;
      default -> status >= 500
          ? CommonErrorCodes.INTERNAL_ERROR
          : CommonErrorCodes.VALIDATION_FAILED;
    };
  }

  private static ApiResponse<ProblemDetail> problem(int status, String errorCode, String message) {
    return ApiResponse.error(status, message, ProblemDetails.of(errorCode, status, message));
  }
}
