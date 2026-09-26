package com.bone.metadata.config;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.core.model.ProblemDetails;
import com.bone.metadata.catalog.common.exception.CatalogIdempotencyConflictException;
import com.bone.metadata.catalog.common.exception.CatalogOptimisticLockException;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.exception.FieldConflictException;
import com.bone.metadata.exception.TooManyRequestsException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * metadata 模块全局异常 → HTTP 状态 + {@link ApiResponse}{@code <ProblemDetail>}。
 *
 * <p><b>i18n 改造（方案 §6.2 / §12.3）</b>：
 *
 * <ul>
 *   <li>基础设施语义（400/403/404/405/409/412/429/500）统一复用 {@link CommonErrorCodes}， 不再自建 META_ 前缀的同义码——
 *       跨模块共用语义只在 bone-core 定义一份，前端 {@code errors.*} 只收录一份键。
 *   <li>工厂统一用 {@link ProblemDetails#of(String, int, String, String)}： traceId 从 MDC 取、title 用 HTTP
 *       状态文本、type 带 errorCode URI——与 IAM / bone-web / system 三模块口径完全一致。 此前自建 {@code
 *       CatalogApiResponses.problem(...)} 已删除。
 *   <li>{@link BizException} 透传 {@code ex.getErrorCode()}（业务码随异常走，handler 不解析 message、也不手填常量）。
 *   <li>{@link HttpMessageNotReadableException} 映射 {@code COMMON_MALFORMED_REQUEST}（JSON 格式错 ≠ Bean
 *       Validation 错）， 不再复用 VALIDATION_FAILED。
 *   <li>{@link RuntimeRecordException} 是 metadata 模块独有运行时异常， 其 {@code getErrorCode()} 返回
 *       META_RUNTIME_* 业务码（非基础设施）， 直接透传。
 * </ul>
 *
 * <p>{@code message} 始终为中文（fallback 契约），展示文案归前端语言包。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  /** Bean Validation / 参数缺失 —— 400。 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleValidation(
      MethodArgumentNotValidException ex) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining(", "));
    return problem(HttpStatus.BAD_REQUEST, CommonErrorCodes.VALIDATION_FAILED, detail);
  }

  /** JSON 格式错误 / 类型不匹配 —— 400。 语义区别于 Bean Validation 的"字段校验失败"。 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleMessageNotReadable(
      HttpMessageNotReadableException ex) {
    String detail = ex.getMostSpecificCause().getMessage();
    return problem(HttpStatus.BAD_REQUEST, CommonErrorCodes.MALFORMED_REQUEST, detail);
  }

  /**
   * metadata runtime 独有业务异常。
   *
   * <p>{@code getErrorCode()} 返回 {@code META_RUNTIME_*} 业务码（非基础设施），直接透传； HTTP 状态按业务码前缀区分。
   */
  @ExceptionHandler(RuntimeRecordException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleRuntimeRecord(RuntimeRecordException ex) {
    HttpStatus status =
        switch (ex.getErrorCode()) {
          case "META_RUNTIME_RECORD_NOT_FOUND", "META_RUNTIME_ENTITY_NOT_FOUND" -> HttpStatus
              .NOT_FOUND;
          case "META_PRECONDITION_FAILED" -> HttpStatus.PRECONDITION_FAILED;
          case "META_RUNTIME_VALIDATION_FAILED" -> HttpStatus.BAD_REQUEST;
          case "META_RUNTIME_DUPLICATE" -> HttpStatus.CONFLICT;
          case "META_RUNTIME_INVALID_QUERY", "META_RUNTIME_INVALID_IDENTIFIER" -> HttpStatus
              .BAD_REQUEST;
          default -> HttpStatus.BAD_REQUEST;
        };
    return problem(status, ex.getErrorCode(), ex.getMessage());
  }

  /**
   * 业务异常 —— 透传 {@link BizException#getErrorCode()}。
   *
   * <p>应用层 {@code *Errors.of(errorCode, detail)} 构造 BizException 时已写入稳定业务码， handler 不解析 message、
   * 也不手填常量——避免"码与状态两处各写一遍"的漂移。 旧构造路径（{@code new BizException(code, msg)}）errorCode 为 null，
   * 前端按回退链降级到中文 message。
   */
  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleBiz(BizException ex) {
    int httpStatus = toHttpStatus(ex.getCode());
    return problem(HttpStatus.valueOf(httpStatus), ex.getErrorCode(), ex.getMessage());
  }

  /**
   * 领域异常兜底 —— 应用层应将 {@link DomainException} 翻译为带业务码的 {@link BizException}。
   *
   * <p>本分支捕获的是未被应用层翻译的漏网之鱼，统一归到 VALIDATION_FAILED， 避免漏到 Exception 兜底成 500。
   */
  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleDomain(DomainException ex) {
    log.warn(
        "Domain exception leaked to handler (should be translated to BizException): {}",
        ex.getMessage());
    return problem(HttpStatus.BAD_REQUEST, CommonErrorCodes.VALIDATION_FAILED, ex.getMessage());
  }

  /** 乐观锁版本不匹配 —— 412。 */
  @ExceptionHandler(CatalogOptimisticLockException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleOptimisticLock(
      CatalogOptimisticLockException ex) {
    return problem(
        HttpStatus.PRECONDITION_FAILED, CommonErrorCodes.PRECONDITION_FAILED, ex.getMessage());
  }

  /** Idempotency-Key 冲突 —— 409。 */
  @ExceptionHandler(CatalogIdempotencyConflictException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleIdempotencyConflict(
      CatalogIdempotencyConflictException ex) {
    return problem(HttpStatus.CONFLICT, CommonErrorCodes.IDEMPOTENCY_CONFLICT, ex.getMessage());
  }

  /** 字段唯一约束冲突 —— 409。 */
  @ExceptionHandler(FieldConflictException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleFieldConflict(FieldConflictException ex) {
    return problem(HttpStatus.CONFLICT, CommonErrorCodes.CONFLICT, ex.getMessage());
  }

  /** 限流 —— 429。 */
  @ExceptionHandler(TooManyRequestsException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleTooManyRequests(
      TooManyRequestsException ex) {
    return problem(HttpStatus.TOO_MANY_REQUESTS, CommonErrorCodes.RATE_LIMITED, ex.getMessage());
  }

  /** 无权限 —— 403（Spring Security {@code @PreAuthorize} 拒绝时抛出）。 */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleAccessDenied(AccessDeniedException ex) {
    log.warn("Access denied: {}", ex.getMessage());
    return problem(HttpStatus.FORBIDDEN, CommonErrorCodes.FORBIDDEN, "无权限访问该资源");
  }

  /** HTTP 方法不被支持 —— 405。必须显式处理， 否则会被下方 catch-all 兜底成 500。 */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex) {
    log.warn("Method not allowed: {}", ex.getMethod());
    return problem(
        HttpStatus.METHOD_NOT_ALLOWED,
        CommonErrorCodes.METHOD_NOT_ALLOWED,
        "请求方法不被支持: " + ex.getMethod());
  }

  /** 路由不存在 —— 404。必须显式处理， 否则会被下方 catch-all 兜底成 500。 */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleNoResourceFound(
      NoResourceFoundException ex) {
    log.warn("No resource found: {}", ex.getResourcePath());
    return problem(
        HttpStatus.NOT_FOUND, CommonErrorCodes.NOT_FOUND, "请求地址不存在: " + ex.getResourcePath());
  }

  /** 兜底 —— 500。 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleGeneral(Exception ex) {
    log.error("未处理异常", ex);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCodes.INTERNAL_ERROR, "系统内部错误");
  }

  // ===== 统一出口 =====

  /**
   * 与 IAM / bone-web / system 三模块完全对齐的统一出口： 用 {@link ProblemDetails#of(String, int, String,
   * String)} 构造 ProblemDetail（traceId 从 MDC 取、title 用 HTTP 状态文本、type 带 errorCode URI）， 不再自建
   * CatalogApiResponses。
   */
  private ResponseEntity<ApiResponse<ProblemDetail>> problem(
      HttpStatus status, String errorCode, String message) {
    ProblemDetail problem = ProblemDetails.of(errorCode, status.value(), message, null);
    return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message, problem));
  }

  /** 业务码 → HTTP 状态码。 若 code 本身是合法 HTTP 状态（400–599）则直用，否则兜底 400。 */
  private static int toHttpStatus(int code) {
    return (code >= 400 && code <= 599) ? code : 400;
  }
}
