package com.bone.iam.infrastructure.config;

import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * IAM 模块全局异常处理器
 *
 * <p>bone-iam 未依赖 bone-web，因此需要自行提供异常处理。 将 BizException 翻译为符合 API 规范的 HTTP 状态码 + ApiResponse 信封。 对齐
 * Bone-API-规范 §4.2 RFC 7807 ProblemDetail 结构。
 */
@Slf4j
@RestControllerAdvice
public class IamExceptionHandler {

  /**
   * 处理业务异常 BizException
   *
   * <p>HTTP 状态码与 ApiResponse.code 对齐，符合 API 规范"禁止 HTTP 2xx 且 success: false"。
   *
   * <p><b>曾有的 {@code @ExceptionHandler(NotFoundException.class)} 已删除</b>：{@code NotFoundException}
   * 本身是 {@link BizException} 的子类（固定 404），本分支对它的处理与之逐字节等价（同状态、同信封）， 而 IAM 内已无任何抛出点 ——单独挂一个 handler
   * 只会让人以为还存在第二套「资源不存在」机制。它若再被抛出，本分支即可正确兜住。
   */
  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<Map<String, Object>>> handleBizException(
      BizException ex, HttpServletRequest request) {
    log.info("[handleBizException] code={}, message={}", ex.getCode(), ex.getMessage());
    int httpStatus = toHttpStatus(ex.getCode());
    Map<String, Object> problemDetail = buildProblemDetail(httpStatus, ex.getMessage(), request);
    return ResponseEntity.status(httpStatus)
        .body(ApiResponse.error(httpStatus, ex.getMessage(), problemDetail));
  }

  /** 处理 Spring Security 权限不足异常（@PreAuthorize 拒绝时抛出），返回 403 */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Map<String, Object>>> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    log.warn(
        "[handleAccessDenied] 权限不足: uri={}, message={}", request.getRequestURI(), ex.getMessage());
    Map<String, Object> problemDetail = buildProblemDetail(403, "无权限访问该资源", request);
    return ResponseEntity.status(403).body(ApiResponse.error(403, "无权限访问该资源", problemDetail));
  }

  /** 处理资源不存在异常（路径匹配不到任何 Controller），返回 404 */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Map<String, Object>>> handleNoResourceFound(
      NoResourceFoundException ex, HttpServletRequest request) {
    log.warn(
        "[handleNoResourceFound] 资源不存在: uri={}, message={}",
        request.getRequestURI(),
        ex.getMessage());
    Map<String, Object> problemDetail = buildProblemDetail(404, "请求的资源不存在", request);
    return ResponseEntity.status(404).body(ApiResponse.error(404, "请求的资源不存在", problemDetail));
  }

  /** 兜底处理所有未捕获异常 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Map<String, Object>>> handleException(
      Exception ex, HttpServletRequest request) {
    log.error(
        "[handleException] 未预期异常: type={}, message={}",
        ex.getClass().getName(),
        ex.getMessage(),
        ex);
    Map<String, Object> problemDetail = buildProblemDetail(500, "系统内部错误", request);
    return ResponseEntity.status(500).body(ApiResponse.error(500, "系统内部错误", problemDetail));
  }

  /** 处理请求体解析异常（JSON 格式错误、类型不匹配等） */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Map<String, Object>>> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    log.warn("[handleHttpMessageNotReadable] {}", ex.getMessage());
    Map<String, Object> problemDetail = buildProblemDetail(400, "请求体格式错误", request);
    return ResponseEntity.badRequest().body(ApiResponse.error(400, "请求体格式错误", problemDetail));
  }

  /** 处理参数校验异常（@NotBlank 等） */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Map<String, Object>>> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream().map(this::toFieldError).toList();
    String message =
        errors.stream()
            .map(e -> e.get("field") + ": " + e.get("message"))
            .reduce((a, b) -> a + "; " + b)
            .orElse("参数校验失败");
    log.warn("[handleValidation] {}", message);
    Map<String, Object> problemDetail = buildProblemDetail(400, message, request);
    problemDetail.put("errors", errors);
    return ResponseEntity.badRequest().body(ApiResponse.error(400, message, problemDetail));
  }

  private Map<String, String> toFieldError(FieldError fe) {
    Map<String, String> error = new LinkedHashMap<>();
    error.put("field", fe.getField());
    error.put("message", fe.getDefaultMessage());
    if (fe.getRejectedValue() != null) {
      error.put("rejectedValue", String.valueOf(fe.getRejectedValue()));
    }
    return error;
  }

  /**
   * 构建 RFC 7807 ProblemDetail 结构。
   *
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7807">RFC 7807</a>
   */
  private Map<String, Object> buildProblemDetail(
      int status, String detail, HttpServletRequest request) {
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("type", "https://bone.wps.cn/problems/" + status);
    problem.put("title", httpStatusText(status));
    problem.put("status", status);
    problem.put("detail", detail);
    problem.put("instance", request != null ? request.getRequestURI() : null);
    problem.put("traceId", MDC.get("traceId"));
    return problem;
  }

  private String httpStatusText(int status) {
    return switch (status) {
      case 400 -> "Bad Request";
      case 401 -> "Unauthorized";
      case 403 -> "Forbidden";
      case 404 -> "Not Found";
      case 409 -> "Conflict";
      case 423 -> "Locked";
      case 500 -> "Internal Server Error";
      default -> "Error";
    };
  }

  /** 将业务错误码映射为 HTTP 状态码。 若 code 本身是合法 HTTP 状态码（400–599）则直接使用，否则默认 400。 */
  private int toHttpStatus(int code) {
    return (code >= 400 && code <= 599) ? code : 400;
  }
}
