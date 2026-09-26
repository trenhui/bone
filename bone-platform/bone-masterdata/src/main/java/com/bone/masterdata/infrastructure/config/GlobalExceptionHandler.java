package com.bone.masterdata.infrastructure.config;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.exception.NotFoundException;
import com.bone.core.exception.SystemException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.core.model.ProblemDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
 * 主数据模块全局异常处理器。
 *
 * <p><b>i18n 改造（方案 §6.2 / §12.3）</b>：此前全部是 {@code ApiResponse.error(code, "中文")}， 失败响应 {@code data}
 * 恒为 {@code null}，前端 {@code showError()} 的 errorCode 分支在本模块永远不命中。 现统一产出 {@link ProblemDetail}（含
 * {@code errorCode}），基础设施语义复用 {@link CommonErrorCodes}， {@code message} 保持中文（fallback 契约）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final int NOT_IMPLEMENTED_CODE = 501;

  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleBusinessException(BizException e) {
    int status = resolveHttpStatus(e.getCode()).value();
    // 码随异常走：MasterDataErrors.of(...) 构造时写入，handler 不解析 message
    return problem(status, e.getErrorCode(), e.getMessage());
  }

  private static HttpStatus resolveHttpStatus(int code) {
    if (code == NOT_IMPLEMENTED_CODE) {
      return HttpStatus.NOT_IMPLEMENTED;
    }
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
   * 并发下的唯一约束冲突 → 409。
   *
   * <p>应用层的前置 count 校验是 check-then-act，挡不住并发插入；真正的保证在数据库唯一索引， 这里把它统一翻译成 409 而不是漏成 500。
   */
  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleDuplicateKey(DuplicateKeyException e) {
    log.warn("唯一约束冲突: {}", e.getMessage());
    return problem(409, CommonErrorCodes.CONFLICT, "记录已存在（唯一约束冲突）");
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
   * <p>Bean Validation 失败、缺参、类型不匹配、非法参数都属于客户端错误； 若缺失这些分支，会被
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
   * <p>必须显式声明：{@code AccessDeniedException} 若漏到 {@code Exception.class} 兜底，「权限不足」会被伪装成
   * 500「系统内部错误」（2026-09-26 联调实测：租户账号调平台域写接口返回 500）。 {@code AuthorizationDeniedException} （Spring
   * Security 6.3+）是其子类，一并覆盖。
   */
  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleAccessDenied(
      org.springframework.security.access.AccessDeniedException e) {
    log.warn("权限不足: {}", e.getMessage());
    return problem(403, CommonErrorCodes.FORBIDDEN, "没有该操作权限");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleException(Exception e) {
    log.error("未处理异常: {}", e.getMessage(), e);
    return problem(500, CommonErrorCodes.INTERNAL_ERROR, "系统内部错误");
  }

  /**
   * 请求地址不存在 → 404（而非被 catch-all 兜底成 500）。
   *
   * <p>Spring 6（Boot 3）下未匹配路由抛出 {@link NoResourceFoundException}；必须显式处理，否则会被
   * {@code @ExceptionHandler(Exception.class)} 兜底成 500，使「接口不存在」与「服务器内部错误」语义混淆。
   */
  // 405 必须显式声明：本类的 Exception 兜底会抢在 bone-web 的 405 映射之前命中，
  // 否则"客户端用错方法"会被伪装成服务端 500。
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException e) {
    log.warn("请求方法不被支持: {}", e.getMethod());
    return problem(405, CommonErrorCodes.METHOD_NOT_ALLOWED, "请求方法不被支持: " + e.getMethod());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleNoResourceFound(
      NoResourceFoundException e) {
    log.warn("No resource found: {}", e.getResourcePath());
    return problem(404, CommonErrorCodes.NOT_FOUND, "请求地址不存在: " + e.getResourcePath());
  }

  private static ResponseEntity<ApiResponse<ProblemDetail>> problem(
      int status, String errorCode, String message) {
    return ResponseEntity.status(status)
        .body(ApiResponse.error(status, message, ProblemDetails.of(errorCode, status, message)));
  }
}
