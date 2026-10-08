package com.bone.studio.generator.config;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.core.model.ProblemDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 模块级异常 → HTTP 映射。
 *
 * <p><b>为什么本模块必须显式有这个类</b>：在此之前 studio-generator 是唯一没有 {@code @RestControllerAdvice} 的服务，任何未被
 * {@code try/catch} 的异常都会穿过 Spring MVC 落到 {@code /error} 页；而本模块的 {@code /error} 渲染本身会抛 {@code
 * NoClassDefFoundError: org/apache/catalina/util/RequestUtil}，最终得到一个 <b>HTTP 500 且 body 完全为空</b>
 * 的响应——真实异常被彻底吞掉，排障只能靠翻日志。实测路径：{@code GET /api/v1/generator/tables?dataSourceId=<不存在的id>} ⇒ 空 500。
 *
 * <p><b>{@code BizException} 为什么不用 {@code @ResponseStatus} 固定 400</b>：其第一个参数是 HTTP 状态（由 {@link
 * com.bone.studio.generator.common.GeneratorErrors} 的「码 → 状态」表提供），而 {@code @ResponseStatus}
 * 是编译期常量，一旦写死 400 就会把所有业务异常的状态钉死，与「HTTP 状态与 {@code ApiResponse.code} 对齐」的契约相悖 （前端按 HTTP 状态分流）。故此处与
 * bone-system / bone-web 同口径：用 {@link ResponseEntity} 动态取状态， 400–599 直用，否则兜底 400。
 *
 * <p><b>{@code IllegalArgumentException} 为什么是 400 而不是 500</b>：本模块的此类异常全部来自「入参不合法 / 资源 id
 * 查不到」（如数据源不存在），属于调用方可修正的错误；进 500 会污染服务端告警口径，也让前端只能展示 「系统内部错误」。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(DomainException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<ProblemDetail> handleDomainException(DomainException e) {
    log.warn("Domain exception: {}", e.getMessage());
    return errorBody(400, CommonErrorCodes.VALIDATION_FAILED, e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<ProblemDetail> handleIllegalArgument(IllegalArgumentException e) {
    log.warn("Illegal argument: {}", e.getMessage());
    return errorBody(400, CommonErrorCodes.VALIDATION_FAILED, e.getMessage());
  }

  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleBizException(BizException e) {
    int code = e.getCode() > 0 ? e.getCode() : 400;
    log.warn("Biz exception [{}]: {}", code, e.getMessage());
    int status = toHttpStatus(code);
    return ResponseEntity.status(status).body(errorBody(status, e.getErrorCode(), e.getMessage()));
  }

  /** 与 bone-web 同一口径：{@code code} 本身是合法 HTTP 状态（400–599）则直用，否则兜底 400。 */
  private static int toHttpStatus(int code) {
    return (code >= 400 && code <= 599) ? code : 400;
  }

  /** 参数校验失败：缺参数 / 类型不匹配 / body 不可解析，均为调用方错误。 */
  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<ProblemDetail> handleBadRequest(Exception e) {
    return errorBody(400, CommonErrorCodes.VALIDATION_FAILED, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {
    String msg =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
            .orElse("参数校验失败");
    return errorBody(400, CommonErrorCodes.VALIDATION_FAILED, msg);
  }

  /** 方法级鉴权失败 → 403。必须显式处理，否则会被下方 {@code handleException} 兜底成 500。 */
  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiResponse<ProblemDetail> handleAccessDenied(AccessDeniedException e) {
    return errorBody(403, CommonErrorCodes.FORBIDDEN, "无访问权限");
  }

  @ExceptionHandler(AuthenticationException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiResponse<ProblemDetail> handleAuthentication(AuthenticationException e) {
    return errorBody(401, CommonErrorCodes.UNAUTHORIZED, "未认证或凭证已失效");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<ProblemDetail> handleNotFound(NoResourceFoundException e) {
    return errorBody(404, CommonErrorCodes.NOT_FOUND, "资源不存在");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
  public ApiResponse<ProblemDetail> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException e) {
    return errorBody(405, CommonErrorCodes.METHOD_NOT_ALLOWED, "请求方法不被支持: " + e.getMethod());
  }

  /**
   * 数据库完整性约束冲突 → 400（而非被下方 catch-all 兜底成 500）。
   *
   * <p>本模块自带 advice（其 bean 名为 {@code globalExceptionHandler}，会使 bone-web 框架 handler 整体让路），
   * 故必须在此显式声明。成因是请求数据不满足表结构，属调用方可纠正的错误；漏到 500 会把客户端笔误计入 5xx 错误预算。响应体用常量文案，原始异常只入日志，避免回显表名/约束名。
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<ProblemDetail> handleDataIntegrityViolation(
      DataIntegrityViolationException e) {
    log.warn("数据完整性约束冲突: {}", e.getMessage());
    return errorBody(400, CommonErrorCodes.VALIDATION_FAILED, "数据违反完整性约束，请检查提交字段");
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<ProblemDetail> handleException(Exception e) {
    log.error("Unexpected exception", e);
    return errorBody(500, CommonErrorCodes.INTERNAL_ERROR, "系统内部错误");
  }

  private static ApiResponse<ProblemDetail> errorBody(
      int status, String errorCode, String message) {
    return ApiResponse.error(status, message, ProblemDetails.of(errorCode, status, message));
  }
}
