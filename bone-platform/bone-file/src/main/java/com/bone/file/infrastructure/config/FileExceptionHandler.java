package com.bone.file.infrastructure.config;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.exception.NotFoundException;
import com.bone.core.exception.SystemException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.core.model.ProblemDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 文件模块全局异常处理器。
 *
 * <p><b>修复FILE-500根因</b>：此前 bone-file 没有 {@code @RestControllerAdvice}，所有 {@link BizException}
 * （{@code FILE_ACCESS_DENIED}=403 / {@code FILE_TENANT_CONTEXT_MISSING}=400 / {@code
 * FILE_NOT_FOUND}=404 等） 未被渲染成结构化 {@link ApiResponse}，直接冒泡成 Spring 默认错误页（{@code
 * {"status":500,...}}）， 前端按 {@code errorCode} 分支的 i18n 永不命中。本类将其统一翻译成带 {@code errorCode} 的 {@link
 * ProblemDetail}。
 *
 * <p><b>码随异常走</b>：{@code BizException} 构造时已写入 HTTP 状态（来自 {@code FileErrors} 的状态表）， handler 不解析
 * message，只把状态与 {@code errorCode} 透传出去，避免配对漂移。
 */
@Slf4j
@RestControllerAdvice
public class FileExceptionHandler {

  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleBusinessException(BizException e) {
    int status = resolveHttpStatus(e.getCode()).value();
    // 码随异常走：FileErrors.of(...) 构造时写入，handler 不解析 message。
    return problem(status, e.getErrorCode(), e.getMessage());
  }

  private static HttpStatus resolveHttpStatus(int code) {
    HttpStatus resolved = HttpStatus.resolve(code);
    if (resolved != null && (resolved.is4xxClientError() || resolved.is5xxServerError())) {
      return resolved;
    }
    return HttpStatus.BAD_REQUEST;
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleNotFoundException(NotFoundException e) {
    return problem(404, CommonErrorCodes.NOT_FOUND, e.getMessage());
  }

  /**
   * 数据库完整性约束冲突（NOT NULL / 外键等，源于客户端数据）→ 400。
   *
   * <p>与 {@code @Valid} / 域规则校验同源：凡校验因故未拦住、请求数据仍违反 DB 约束，应归为客户端错误而非 500 系统故障。
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleDataIntegrityViolation(
      DataIntegrityViolationException e) {
    log.warn("数据完整性约束冲突: {}", e.getMessage());
    return problem(400, CommonErrorCodes.VALIDATION_FAILED, "数据违反完整性约束: " + e.getMessage());
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleDomainException(DomainException e) {
    return problem(400, CommonErrorCodes.VALIDATION_FAILED, e.getMessage());
  }

  @ExceptionHandler(SystemException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleSystemException(SystemException e) {
    log.error("系统异常: {}", e.getMessage(), e);
    return problem(500, CommonErrorCodes.INTERNAL_ERROR, e.getMessage());
  }

  /** 请求体无法解析（JSON 格式错误 / 类型不匹配）——比通用"参数不合法"更精确，单独声明。 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleMalformedBody(
      HttpMessageNotReadableException e) {
    log.warn("请求体无法解析: {}", e.getMessage());
    return problem(400, CommonErrorCodes.MALFORMED_REQUEST, "请求体格式错误");
  }

  /**
   * 请求入参不合法 → 400。
   *
   * <p>Bean Validation 失败、缺参、类型不匹配、非法参数都属于客户端错误；若缺失这些分支， 会被
   * {@code @ExceptionHandler(Exception.class)} 兜底成 500，使客户端错误与服务端故障无法区分。
   */
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<ApiResponse<ProblemDetail>> handleBadRequest(Exception e) {
    log.warn("请求参数不合法: {}", e.getMessage());
    return problem(400, CommonErrorCodes.VALIDATION_FAILED, "请求参数不合法: " + e.getMessage());
  }

  /**
   * 方法安全（@PreAuthorize）拒绝 → 403（而非被 catch-all 兜底成 500）。
   *
   * <p>必须显式声明：{@code AccessDeniedException} 若漏到 {@code Exception.class} 兜底，「权限不足」会被伪装成 500。
   */
  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleAccessDenied(
      org.springframework.security.access.AccessDeniedException e) {
    log.warn("权限不足: {}", e.getMessage());
    return problem(403, CommonErrorCodes.FORBIDDEN, "没有该操作权限");
  }

  // 405 必须显式声明：本类的 Exception 兜底会抢在 bone-web 的 405 映射之前命中，
  // 否则"客户端用错方法"会被伪装成服务端 500。
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException e) {
    log.warn("请求方法不被支持: {}", e.getMethod());
    return problem(405, CommonErrorCodes.METHOD_NOT_ALLOWED, "请求方法不被支持: " + e.getMethod());
  }

  /**
   * 请求地址不存在 → 404（而非被 catch-all 兜底成 500）。
   *
   * <p>Spring 6（Boot 3）下未匹配路由抛出 {@link NoResourceFoundException}；必须显式处理，否则会被
   * {@code @ExceptionHandler(Exception.class)} 兜底成 500，使「接口不存在」与「服务器内部错误」语义混淆。
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleNoResourceFound(
      NoResourceFoundException e) {
    log.warn("No resource found: {}", e.getResourcePath());
    return problem(404, CommonErrorCodes.NOT_FOUND, "请求地址不存在: " + e.getResourcePath());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleException(Exception e) {
    log.error("未处理异常: {}", e.getMessage(), e);
    return problem(500, CommonErrorCodes.INTERNAL_ERROR, "系统内部错误");
  }

  private static ResponseEntity<ApiResponse<ProblemDetail>> problem(
      int status, String errorCode, String message) {
    return ResponseEntity.status(status)
        .body(ApiResponse.error(status, message, ProblemDetails.of(errorCode, status, message)));
  }
}
