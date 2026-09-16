package com.bone.core.exception;

import static com.bone.core.enums.GlobalErrorCodeConstants.*;

import com.bone.core.model.ApiResponse;
import com.bone.core.util.ExceptionUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器，将 Exception 翻译成 ApiResponse + 对应的异常编号。
 *
 * <p><b>HTTP 状态与信封 code 必须一致</b>：API 规范 §2.4 明令「禁止 HTTP 2xx 且 {@code success: false}」。因此每个 处理器都返回
 * {@link ResponseEntity} 并显式给出状态码——只返回 {@code ApiResponse} 会让错误以 HTTP 200 送达，
 * 客户端与监控（SLI/告警按状态码统计）都会把它当成功。返回 {@code ResponseEntity} 的写法与 {@link #bizExceptionHandler} 保持一致。
 *
 * <p><b>状态码来源</b>：业务异常取异常自带的 code（经 {@link #toHttpStatus}），框架异常按其语义取固定状态（400 / 404 / 405 / 429 /
 * 500）。
 */
@RestControllerAdvice
@AllArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

  /**
   * 处理所有异常，主要是提供给 Filter 使用 因为 Filter 不走 SpringMVC 的流程，但是我们又需要兜底处理异常，所以这里提供一个全量的异常处理过程，保持逻辑统一。
   *
   * <p>返回 {@code ApiResponse} 供 Filter 自行写出（Filter 场景下状态码由写出方设置），故此处取 {@code getBody()}。
   *
   * @param request 请求
   * @param ex 异常
   * @return 通用返回
   */
  public ApiResponse<?> allExceptionHandler(HttpServletRequest request, Throwable ex) {
    if (ex instanceof MissingServletRequestParameterException) {
      return missingServletRequestParameterExceptionHandler(
              (MissingServletRequestParameterException) ex)
          .getBody();
    }
    if (ex instanceof MethodArgumentTypeMismatchException) {
      return methodArgumentTypeMismatchExceptionHandler((MethodArgumentTypeMismatchException) ex)
          .getBody();
    }
    if (ex instanceof MethodArgumentNotValidException) {
      return methodArgumentNotValidExceptionExceptionHandler((MethodArgumentNotValidException) ex)
          .getBody();
    }
    if (ex instanceof BindException) {
      return bindExceptionHandler((BindException) ex).getBody();
    }
    if (ex instanceof ConstraintViolationException) {
      return constraintViolationExceptionHandler((ConstraintViolationException) ex).getBody();
    }
    if (ex instanceof ValidationException) {
      return validationException((ValidationException) ex).getBody();
    }
    if (ex instanceof NoHandlerFoundException) {
      return noHandlerFoundExceptionHandler((NoHandlerFoundException) ex).getBody();
    }
    if (ex instanceof HttpRequestMethodNotSupportedException) {
      return httpRequestMethodNotSupportedExceptionHandler(
              (HttpRequestMethodNotSupportedException) ex)
          .getBody();
    }
    if (ex instanceof ServiceException) {
      return serviceExceptionHandler((ServiceException) ex).getBody();
    }
    if (ex instanceof SystemException) {
      return systemExceptionHandler((SystemException) ex).getBody();
    }
    if (ex instanceof InfrastructureException) {
      return infrastructureExceptionHandler((InfrastructureException) ex).getBody();
    }

    if (ex instanceof BizException) {
      return bizExceptionHandler((BizException) ex).getBody();
    }
    return defaultExceptionHandler(request, ex).getBody();
  }

  /**
   * 处理 SpringMVC 请求参数缺失
   *
   * <p>例如说，接口上设置了 @RequestParam("xx") 参数，结果并未传递 xx 参数
   */
  @ExceptionHandler(value = MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResponse<?>> missingServletRequestParameterExceptionHandler(
      MissingServletRequestParameterException ex) {
    log.warn("[missingServletRequestParameterExceptionHandler]", ex);
    return ResponseEntity.status(BAD_REQUEST.getCode())
        .body(
            ApiResponse.error(
                BAD_REQUEST.getCode(), String.format("请求参数缺失:%s", ex.getParameterName())));
  }

  /**
   * 处理 SpringMVC 请求参数类型错误
   *
   * <p>例如说，接口上设置了 @RequestParam("xx") 参数为 Integer，结果传递 xx 参数类型为 String
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<?>> methodArgumentTypeMismatchExceptionHandler(
      MethodArgumentTypeMismatchException ex) {
    log.warn("[methodArgumentTypeMismatchExceptionHandler]", ex);
    return ResponseEntity.status(BAD_REQUEST.getCode())
        .body(
            ApiResponse.error(
                BAD_REQUEST.getCode(), String.format("请求参数类型错误:%s", ex.getMessage())));
  }

  /** 处理 SpringMVC 参数校验不正确 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<?>> methodArgumentNotValidExceptionExceptionHandler(
      MethodArgumentNotValidException ex) {
    log.warn("[methodArgumentNotValidExceptionExceptionHandler]", ex);
    FieldError fieldError = ex.getBindingResult().getFieldError();
    assert fieldError != null; // 断言，避免告警
    return ResponseEntity.status(BAD_REQUEST.getCode())
        .body(
            ApiResponse.error(
                BAD_REQUEST.getCode(),
                String.format("请求参数不正确:%s", fieldError.getDefaultMessage())));
  }

  /** 处理 SpringMVC 参数绑定不正确，本质上也是通过 Validator 校验 */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<ApiResponse<?>> bindExceptionHandler(BindException ex) {
    log.warn("[handleBindException]", ex);
    FieldError fieldError = ex.getFieldError();
    assert fieldError != null; // 断言，避免告警
    return ResponseEntity.status(BAD_REQUEST.getCode())
        .body(
            ApiResponse.error(
                BAD_REQUEST.getCode(),
                String.format("请求参数不正确:%s", fieldError.getDefaultMessage())));
  }

  /** 处理 Validator 校验不通过产生的异常 */
  @ExceptionHandler(value = ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<?>> constraintViolationExceptionHandler(
      ConstraintViolationException ex) {
    log.warn("[constraintViolationExceptionHandler]", ex);
    ConstraintViolation<?> constraintViolation = ex.getConstraintViolations().iterator().next();
    return ResponseEntity.status(BAD_REQUEST.getCode())
        .body(
            ApiResponse.error(
                BAD_REQUEST.getCode(),
                String.format("请求参数不正确:%s", constraintViolation.getMessage())));
  }

  /** 处理 Dubbo Consumer 本地参数校验时，抛出的 ValidationException 异常 */
  @ExceptionHandler(value = ValidationException.class)
  public ResponseEntity<ApiResponse<?>> validationException(ValidationException ex) {
    log.warn("[constraintViolationExceptionHandler]", ex);
    // 无法拼接明细的错误信息，因为 Dubbo Consumer 抛出 ValidationException 异常时，是直接的字符串信息，且人类不可读
    return ResponseEntity.status(BAD_REQUEST.getCode())
        .body(ApiResponse.error(BAD_REQUEST.getCode(), "constraintViolationExceptionHandler"));
  }

  /**
   * 处理 SpringMVC 请求地址不存在
   *
   * <p>注意，它需要设置如下两个配置项： 1. spring.mvc.throw-exception-if-no-handler-found 为 true 2.
   * spring.mvc.static-path-pattern 为 /statics/**
   */
  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ApiResponse<?>> noHandlerFoundExceptionHandler(NoHandlerFoundException ex) {
    log.warn("[noHandlerFoundExceptionHandler]", ex);
    return ResponseEntity.status(NOT_FOUND.getCode())
        .body(
            ApiResponse.error(
                NOT_FOUND.getCode(), String.format("请求地址不存在:%s", ex.getRequestURL())));
  }

  /**
   * 处理 SpringMVC 请求方法不正确
   *
   * <p>例如说，A 接口的方法为 GET 方式，结果请求方法为 POST 方式，导致不匹配
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<?>> httpRequestMethodNotSupportedExceptionHandler(
      HttpRequestMethodNotSupportedException ex) {
    log.warn("[httpRequestMethodNotSupportedExceptionHandler]", ex);
    return ResponseEntity.status(METHOD_NOT_ALLOWED.getCode())
        .body(
            ApiResponse.error(
                METHOD_NOT_ALLOWED.getCode(), String.format("请求方法不正确:%s", ex.getMessage())));
  }

  /** 处理 Resilience4j 限流抛出的异常 */
  public ResponseEntity<ApiResponse<?>> requestNotPermittedExceptionHandler(
      HttpServletRequest req, Throwable ex) {
    log.warn("[requestNotPermittedExceptionHandler][url({}) 访问过于频繁]", req.getRequestURL(), ex);
    return ResponseEntity.status(TOO_MANY_REQUESTS.getCode())
        .body(
            ApiResponse.error(
                TOO_MANY_REQUESTS.getCode(),
                String.format(
                    "[requestNotPermittedExceptionHandler][url(%s) 访问过于频繁]", req.getRequestURL())));
  }

  /**
   * 处理服务异常 ServiceException
   *
   * <p>
   */
  @ExceptionHandler(value = ServiceException.class)
  public ResponseEntity<ApiResponse<?>> serviceExceptionHandler(ServiceException ex) {
    log.info("[serviceExceptionHandler]", ex);
    return ResponseEntity.status(toHttpStatus(ex.getCode()))
        .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
  }

  @ExceptionHandler(value = SystemException.class)
  public ResponseEntity<ApiResponse<?>> systemExceptionHandler(SystemException ex) {
    log.error("[systemExceptionHandler]", ex);
    return ResponseEntity.status(INTERNAL_SERVER_ERROR.getCode())
        .body(ApiResponse.error(INTERNAL_SERVER_ERROR.getCode(), ex.getMessage()));
  }

  @ExceptionHandler(value = InfrastructureException.class)
  public ResponseEntity<ApiResponse<?>> infrastructureExceptionHandler(InfrastructureException ex) {
    log.error("[infrastructureExceptionHandler]", ex);
    return ResponseEntity.status(INTERNAL_SERVER_ERROR.getCode())
        .body(ApiResponse.error(INTERNAL_SERVER_ERROR.getCode(), ex.getMessage()));
  }

  /**
   * 处理业务异常 BizException
   *
   * <p>例如说，商品库存不足，用户手机号已存在。
   *
   * <p>HTTP 状态码与 ApiResponse.code 对齐，符合 API 规范"禁止 HTTP 2xx 且 success: false"。
   */
  @ExceptionHandler(value = BizException.class)
  public ResponseEntity<ApiResponse<?>> bizExceptionHandler(BizException ex) {
    log.info("[bizExceptionHandler]", ex);
    int httpStatus = toHttpStatus(ex.getCode());
    return ResponseEntity.status(httpStatus).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
  }

  /** 将业务错误码映射为 HTTP 状态码。 若 code 本身是合法 HTTP 状态码（400–599）则直接使用，否则默认 400。 */
  private int toHttpStatus(int code) {
    return (code >= 400 && code <= 599) ? code : 400;
  }

  /** 处理系统异常，兜底处理所有的一切 */
  @ExceptionHandler(value = Exception.class)
  public ResponseEntity<ApiResponse<?>> defaultExceptionHandler(
      HttpServletRequest req, Throwable ex) {
    // 情况一：处理表不存在的异常
    ApiResponse<?> tableNotExistsResult = handleTableNotExists(ex);
    if (tableNotExistsResult != null) {
      return ResponseEntity.status(INTERNAL_SERVER_ERROR.getCode()).body(tableNotExistsResult);
    }

    // 情况二：部分特殊的库的处理
    if (Objects.equals(
        "io.github.resilience4j.ratelimiter.RequestNotPermitted", ex.getClass().getName())) {
      return requestNotPermittedExceptionHandler(req, ex);
    }

    Throwable cause = ex.getCause();
    if (cause instanceof InvocationTargetException) {
      log.error("[handleInvocationTargetException - cause]", cause);
      return ResponseEntity.status(INTERNAL_SERVER_ERROR.getCode())
          .body(ApiResponse.error(INTERNAL_SERVER_ERROR.getCode(), cause.getMessage()));
    }

    // 情况三：处理异常
    log.error("[defaultExceptionHandler]", ex);
    // 插入异常日志
    this.createExceptionLog(req, ex);
    // 返回 ERROR ApiResponse
    return ResponseEntity.status(INTERNAL_SERVER_ERROR.getCode())
        .body(ApiResponse.error(INTERNAL_SERVER_ERROR.getCode(), INTERNAL_SERVER_ERROR.getMsg()));
  }

  private void createExceptionLog(HttpServletRequest req, Throwable e) {
    // 插入错误日志
    // ApiErrorLog errorLog = new ApiErrorLog();
    try {
      // 初始化 errorLog
      // initExceptionLog(errorLog, req, e);
      // 执行插入 errorLog
      //  apiErrorLogFrameworkService.createApiErrorLog(errorLog);
    } catch (Throwable th) {
      // log.error("[createExceptionLog][url({}) log({}) 发生异常]", req.getRequestURI(),
      // JSONUtil.toJsonStr(errorLog), th);
    }
  }

  /**
   * 处理 Table 不存在的异常情况
   *
   * @param ex 异常
   * @return 如果是 Table 不存在的异常，则返回对应的 ApiResponse
   */
  private ApiResponse<?> handleTableNotExists(Throwable ex) {
    String message = ExceptionUtil.getRootCauseMessage(ex);
    if (!message.contains("doesn't exist")) {
      return null;
    }
    return null;
  }

  @ExceptionHandler(SQLException.class)
  public ResponseEntity<ApiResponse<?>> handleSQLException(SQLException ex) {
    log.error("[SQLException]", ex);
    return ResponseEntity.status(INTERNAL_SERVER_ERROR.getCode())
        .body(ApiResponse.error(INTERNAL_SERVER_ERROR.getCode(), ex.getMessage()));
  }
}
