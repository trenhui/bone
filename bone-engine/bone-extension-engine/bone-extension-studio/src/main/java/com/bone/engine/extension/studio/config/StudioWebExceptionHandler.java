package com.bone.engine.extension.studio.config;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.engine.extension.studio.application.support.StudioCommandResponses;
import com.bone.engine.extension.studio.common.StudioErrorCodes;
import com.bone.engine.extension.studio.common.exception.IdempotencyConflictException;
import com.bone.engine.extension.studio.common.exception.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Studio API 统一异常 → {@link ApiResponse} + {@link ProblemDetail}。 */
@RestControllerAdvice(basePackages = "com.bone.engine.extension.studio.adapter.web.controller")
public class StudioWebExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(StudioWebExceptionHandler.class);

  /**
   * 携带业务码的异常必须显式声明：本类末尾的 {@link Exception} 兜底会抢在 bone-web 的全局映射之前命中， 若不声明，{@code
   * StudioErrors.of(...)} 抛出的 404/409 会被兜底成 500 并丢掉 errorCode。
   *
   * <p>状态口径：{@link BizException#getCode()} 已是 HTTP 状态语义，400–599 直用；越界值按 500 处理 （与 bone-web 全局
   * handler 同口径）。
   */
  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> bizException(BizException ex) {
    int rawStatus = ex.getCode();
    int status = rawStatus >= 400 && rawStatus <= 599 ? rawStatus : 500;
    String errorCode =
        ex.getErrorCode() != null
            ? ex.getErrorCode()
            : (status >= 500
                ? CommonErrorCodes.INTERNAL_ERROR
                : CommonErrorCodes.VALIDATION_FAILED);
    return problem(HttpStatus.valueOf(status), errorCode, ex.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> forbidden(AccessDeniedException ex) {
    return StudioCommandResponses.problem(
        HttpStatus.FORBIDDEN, CommonErrorCodes.FORBIDDEN, "无权限访问");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> badRequest(IllegalArgumentException ex) {
    return problem(HttpStatus.BAD_REQUEST, CommonErrorCodes.VALIDATION_FAILED, ex.getMessage());
  }

  /**
   * 数据库完整性约束冲突 → 400（而非被下方 {@link #internal} 的 {@code Exception} 兜底成 500）。
   *
   * <p><b>2026-10-08 实测补录</b>：本 advice {@code @RestControllerAdvice(basePackages=...)} 限定在
   * controller 包， 其 {@code Exception} 兜底会<b>抢在</b> bone-web 框架基类的同名分支之前命中，故框架基类的 {@code
   * DataIntegrityViolationException}→400 分支对本模块<b>不生效</b>，必须在此显式声明。 该异常 100%
   * 由客户端输入触发（字段长度/非空是表结构的一部分），报 500 会误导调用方重试并污染 5xx 错误预算。
   *
   * <p>响应体用常量文案、原始异常只入日志（{@code ex.getMessage()} 含完整 SQL 与列名，回显等于泄露表结构）。
   */
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> dataIntegrityViolation(
      org.springframework.dao.DataIntegrityViolationException ex) {
    log.warn("[API] 数据完整性约束冲突: {}", ex.getMessage());
    return problem(HttpStatus.BAD_REQUEST, CommonErrorCodes.VALIDATION_FAILED, "数据违反完整性约束，请检查提交字段");
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> conflict(IllegalStateException ex) {
    return problem(HttpStatus.CONFLICT, StudioErrorCodes.STATE_INVALID, ex.getMessage());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> idempotencyConflict(
      IdempotencyConflictException ex) {
    return StudioCommandResponses.problem(
        HttpStatus.CONFLICT, CommonErrorCodes.IDEMPOTENCY_CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(OptimisticLockException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> optimisticLock(OptimisticLockException ex) {
    return StudioCommandResponses.problem(
        HttpStatus.PRECONDITION_FAILED, CommonErrorCodes.PRECONDITION_FAILED, ex.getMessage());
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> missingRequestPart(
      MissingServletRequestPartException ex) {
    return problem(HttpStatus.BAD_REQUEST, CommonErrorCodes.VALIDATION_FAILED, ex.getMessage());
  }

  // 405 必须显式声明：本类的 Exception 兜底会抢在 bone-web 的 405 映射之前命中，
  // 否则"客户端用错方法"会被伪装成服务端 500。
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> methodNotAllowed(
      HttpRequestMethodNotSupportedException ex) {
    return problem(
        HttpStatus.METHOD_NOT_ALLOWED,
        CommonErrorCodes.VALIDATION_FAILED,
        "请求方法不被支持: " + ex.getMethod());
  }

  /**
   * Spring MVC 输入绑定/解析异常统一归 400。
   *
   * <p><b>为什么必须显式映射</b>：这四类是<b>客户端输入错误</b>，不在此拦截就会落到 {@link #internal}的 {@code Exception}兜底，被记成 500
   * —— 把调用方笔误算进服务端故障预算。本模块 pom 只依赖 bone-core， 框架级 bone-web 的 {@code GlobalExceptionHandler} 不在
   * classpath，无法替本模块补位。
   *
   * <p>响应体用固定文案而非 {@code ex.getMessage()}：后者会原样回显用户输入（类型转换异常里含原始 字符串），属反射型输入回显。
   */
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ApiResponse<ProblemDetail>> badRequest(Exception ex) {
    log.warn("[API] 非法请求入参 traceId={}", MDC.get(StudioRequestContextFilter.TRACE_ID));
    return problem(HttpStatus.BAD_REQUEST, CommonErrorCodes.VALIDATION_FAILED, "请求参数不合法");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> internal(Exception ex) {
    log.error("[API] unhandled traceId={}", MDC.get(StudioRequestContextFilter.TRACE_ID), ex);
    return StudioCommandResponses.problem(
        HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCodes.INTERNAL_ERROR, "服务内部错误");
  }

  private static ResponseEntity<ApiResponse<ProblemDetail>> problem(
      HttpStatus status, String errorCode, String detail) {
    return StudioCommandResponses.problem(status, errorCode, detail);
  }
}
