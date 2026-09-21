package com.bone.system.infrastructure.config;

import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 模块级异常 → HTTP 映射。
 *
 * <p><b>{@code BizException} 为什么不用 {@code @ResponseStatus} 固定 400</b>：{@code BizException} 的第一个参数就是
 * HTTP 状态（{@link com.bone.system.common.SystemErrors} 的「码 → 状态」表提供），而 {@code @ResponseStatus}
 * 是编译期常量——一旦写上 400，所有业务异常的 HTTP 状态都被钉死在 400， 只有响应体里的 {@code code} 才是真实语义，与 bone-web 的「HTTP 状态码与
 * {@code ApiResponse.code} 对齐」契约相悖（前端按 HTTP 状态分流，body 里的 409 就被丢掉了）。故此处改为 {@link ResponseEntity}
 * 动态取状态，口径与 bone-web {@code bizExceptionHandler} 一致：400–599 直用，否则兜底 400。
 *
 * <p>其余分支映射的是「异常类型 → 唯一固定状态」（403/404/401/400/500），不存在对齐问题， 保留 {@code @ResponseStatus}。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(DomainException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleDomainException(DomainException e) {
    log.warn("Domain exception: {}", e.getMessage());
    return ApiResponse.error(400, e.getMessage());
  }

  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<Void>> handleBizException(BizException e) {
    int code = e.getCode() > 0 ? e.getCode() : 400;
    log.warn("Biz exception [{}]: {}", code, e.getMessage());
    return ResponseEntity.status(toHttpStatus(code)).body(ApiResponse.error(code, e.getMessage()));
  }

  /** 与 bone-web 同一口径：{@code code} 本身是合法 HTTP 状态（400–599）则直用，否则兜底 400。 */
  private static int toHttpStatus(int code) {
    return (code >= 400 && code <= 599) ? code : 400;
  }

  /**
   * 方法级 {@code @PreAuthorize} 失败 → 403。必须显式处理， 否则会被下方 {@code @ExceptionHandler(Exception.class)}
   * 兜底为 500。
   */
  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiResponse<Void> handleAccessDeniedException(AccessDeniedException e) {
    log.warn("Access denied: {}", e.getMessage());
    return ApiResponse.error(403, "Forbidden");
  }

  /**
   * 请求地址不存在 → 404（而非被下方 catch-all 兜底成 500）。
   *
   * <p>Spring 6（Boot 3）下未匹配路由抛出 {@link NoResourceFoundException}；本项目开启了 {@code
   * throw-exception-if-no-handler-found}（bone-web 全局异常处理依赖它），因此该异常会冒泡到此处。 必须显式处理为 404，否则会被
   * {@code @ExceptionHandler(Exception.class)} 兜底成 500，使「接口不存在」与 「服务器内部错误」语义混淆，也掩盖了契约对账里真正的缺失路由。
   */
  @ExceptionHandler(NoResourceFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> handleNoResourceFoundException(NoResourceFoundException e) {
    log.warn("No resource found: {}", e.getResourcePath());
    return ApiResponse.error(404, "请求地址不存在: " + e.getResourcePath());
  }

  /** 认证失败 → 401（Spring Security 通常在 filter 层就处理，留兜底）。 */
  @ExceptionHandler(AuthenticationException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiResponse<Void> handleAuthenticationException(AuthenticationException e) {
    log.warn("Authentication failed: {}", e.getMessage());
    return ApiResponse.error(401, "Unauthorized");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
    return ApiResponse.error(400, message);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<Void> handleException(Exception e) {
    log.error("Unexpected exception", e);
    return ApiResponse.error(500, "Internal server error");
  }
}
