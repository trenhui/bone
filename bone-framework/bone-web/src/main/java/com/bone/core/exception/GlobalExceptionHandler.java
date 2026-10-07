package com.bone.core.exception;

import static com.bone.core.enums.GlobalErrorCodeConstants.*;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.core.model.ProblemDetails;
import com.bone.core.util.ExceptionUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

  // ===== 统一 ProblemDetail 出口（i18n 方案 §6.2.1）=====
  //
  // 此前所有分支都是 ApiResponse.error(code, "中文")，失败响应 data 恒为 null，
  // 前端 showError() 的 errorCode 分支永远不命中 —— 英文用户只能看到中文 toast。
  // 下面每个分支都改为产出 ProblemDetail（含 errorCode），message 仍保持中文（fallback 契约）。

  /** 构造带 errorCode 的错误响应体（HTTP 状态与信封 code 一致）。 */
  private ResponseEntity<ApiResponse<?>> problemResponse(
      int status, String errorCode, String message) {
    return ResponseEntity.status(status)
        .body(ApiResponse.error(status, message, problem(status, errorCode, message)));
  }

  /** 构造带结构化字段错误的错误响应体（校验类）。 */
  private ResponseEntity<ApiResponse<?>> problemResponse(
      int status, String errorCode, String message, List<ProblemDetail.FieldError> fieldErrors) {
    ProblemDetail problem = problem(status, errorCode, message);
    if (fieldErrors != null && !fieldErrors.isEmpty()) {
      problem.setErrors(fieldErrors);
    }
    return ResponseEntity.status(status).body(ApiResponse.error(status, message, problem));
  }

  private ProblemDetail problem(int status, String errorCode, String detail) {
    return ProblemDetails.of(errorCode, status, detail, currentUri());
  }

  private static String currentUri() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return attrs.getRequest() == null ? null : attrs.getRequest().getRequestURI();
    }
    return null;
  }

  /** BindingResult → 结构化字段错误（供前端 i18n 逐字段展示）。 */
  private static List<ProblemDetail.FieldError> toFieldErrors(BindingResult bindingResult) {
    if (bindingResult == null) {
      return List.of();
    }
    List<ProblemDetail.FieldError> errors = new ArrayList<>();
    for (FieldError fe : bindingResult.getFieldErrors()) {
      ProblemDetail.FieldError e = new ProblemDetail.FieldError();
      e.setField(fe.getField());
      e.setMessage(fe.getDefaultMessage());
      e.setRejectedValue(fe.getRejectedValue());
      errors.add(e);
    }
    return errors;
  }

  /** ConstraintViolation 集合 → 结构化字段错误。 */
  private static List<ProblemDetail.FieldError> toFieldErrors(
      Iterable<ConstraintViolation<?>> violations) {
    List<ProblemDetail.FieldError> errors = new ArrayList<>();
    for (ConstraintViolation<?> v : violations) {
      ProblemDetail.FieldError e = new ProblemDetail.FieldError();
      e.setField(String.valueOf(v.getPropertyPath()));
      e.setMessage(v.getMessage());
      e.setRejectedValue(v.getInvalidValue());
      errors.add(e);
    }
    return errors;
  }

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
    // 与 MVC 侧同语义：@PreAuthorize 失败必须落到 403，否则鉴权失败仍会被兜底成 500。
    // 两条路径（Filter 的 allExceptionHandler / MVC 的 catch-all）判定必须一致，
    // 避免同一异常在不同入口返回不同状态码。
    if (isAccessDenied(ex)) {
      return accessDeniedExceptionHandler(ex).getBody();
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
    return problemResponse(
        BAD_REQUEST.getCode(),
        CommonErrorCodes.VALIDATION_FAILED,
        String.format("请求参数缺失:%s", ex.getParameterName()));
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
    return problemResponse(
        BAD_REQUEST.getCode(),
        CommonErrorCodes.VALIDATION_FAILED,
        String.format("请求参数类型错误:%s", ex.getMessage()));
  }

  /** 处理 SpringMVC 参数校验不正确 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<?>> methodArgumentNotValidExceptionExceptionHandler(
      MethodArgumentNotValidException ex) {
    log.warn("[methodArgumentNotValidExceptionExceptionHandler]", ex);
    FieldError fieldError = ex.getBindingResult().getFieldError();
    assert fieldError != null; // 断言，避免告警
    return problemResponse(
        BAD_REQUEST.getCode(),
        CommonErrorCodes.VALIDATION_FAILED,
        String.format("请求参数不正确:%s", fieldError.getDefaultMessage()),
        toFieldErrors(ex.getBindingResult()));
  }

  /** 处理 SpringMVC 参数绑定不正确，本质上也是通过 Validator 校验 */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<ApiResponse<?>> bindExceptionHandler(BindException ex) {
    log.warn("[handleBindException]", ex);
    FieldError fieldError = ex.getFieldError();
    assert fieldError != null; // 断言，避免告警
    return problemResponse(
        BAD_REQUEST.getCode(),
        CommonErrorCodes.VALIDATION_FAILED,
        String.format("请求参数不正确:%s", fieldError.getDefaultMessage()),
        toFieldErrors(ex.getBindingResult()));
  }

  /** 处理 Validator 校验不通过产生的异常 */
  @ExceptionHandler(value = ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<?>> constraintViolationExceptionHandler(
      ConstraintViolationException ex) {
    log.warn("[constraintViolationExceptionHandler]", ex);
    ConstraintViolation<?> constraintViolation = ex.getConstraintViolations().iterator().next();
    return problemResponse(
        BAD_REQUEST.getCode(),
        CommonErrorCodes.VALIDATION_FAILED,
        String.format("请求参数不正确:%s", constraintViolation.getMessage()),
        toFieldErrors(ex.getConstraintViolations()));
  }

  /** 处理 Dubbo Consumer 本地参数校验时，抛出的 ValidationException 异常 */
  @ExceptionHandler(value = ValidationException.class)
  public ResponseEntity<ApiResponse<?>> validationException(ValidationException ex) {
    log.warn("[constraintViolationExceptionHandler]", ex);
    // 无法拼接明细的错误信息，因为 Dubbo Consumer 抛出 ValidationException 异常时，是直接的字符串信息，且人类不可读
    return problemResponse(
        BAD_REQUEST.getCode(),
        CommonErrorCodes.VALIDATION_FAILED,
        "constraintViolationExceptionHandler");
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
    return problemResponse(
        NOT_FOUND.getCode(),
        CommonErrorCodes.NOT_FOUND,
        String.format("请求地址不存在:%s", ex.getRequestURL()));
  }

  /**
   * 处理 Spring 6（Boot 3）下未匹配路由抛出的 {@link NoResourceFoundException}。
   *
   * <p>Spring 6 用 {@code NoResourceFoundException} 取代了旧版 {@link NoHandlerFoundException}，因此仅捕获后者（见
   * {@link #noHandlerFoundExceptionHandler}）已无法拦截「接口不存在」的 404，异常会冒泡到 catch-all 兜底成 500。这里补上 {@code
   * NoResourceFoundException} 的专属处理，使契约对账里「路由缺失」与「服务器内部错误」语义正确区分（HTTP 404 + 错误码信封）。
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<?>> noResourceFoundExceptionHandler(
      NoResourceFoundException ex) {
    log.warn("[noResourceFoundExceptionHandler] {}", ex.getResourcePath());
    return problemResponse(
        NOT_FOUND.getCode(),
        CommonErrorCodes.NOT_FOUND,
        String.format("请求地址不存在:%s", ex.getResourcePath()));
  }

  /**
   * 判断异常是否为 Spring Security 的「已认证但权限不足」（HTTP 403 语义）。
   *
   * <p>委托给 {@link AccessDeniedDetector}：判定逻辑需要被<b>有 security 的模块</b>用真实子类断言，而本类所在模块 不依赖
   * security，故把「按类型解析 + 子类覆盖 + 解析失败告警」集中到独立工具类，见其 javadoc。
   */
  private static boolean isAccessDenied(Throwable ex) {
    return AccessDeniedDetector.isAccessDenied(ex);
  }

  /**
   * 处理 Spring Security 鉴权失败（HTTP 403）。
   *
   * <p><b>为何必须显式接管</b>：{@code @PreAuthorize} 校验失败抛的异常既不是 {@link BizException} 也不是 {@link
   * ServiceException}，此前会一路冒泡到 {@code @ExceptionHandler(Exception.class)} 兜底分支， 被翻译成 <b>HTTP 500
   * COMMON_INTERNAL_ERROR</b>。后果有两层：一是语义错误——「无权访问」被报成 「服务器内部出错」，运维会把权限问题当故障排查；二是可观测性受损——SLI
   * 按状态码统计时， 鉴权失败会污染 5xx 错误预算，掩盖真实的服务端异常。
   *
   * <p><b>日志级别取 WARN 而非 ERROR</b>：鉴权拦截是系统按预期工作的表现，不是故障；打成 ERROR 会让它与
   * 真实故障混在一起。但重复出现可能意味着越权试探，故保留告警级别的留痕。
   */
  public ResponseEntity<ApiResponse<?>> accessDeniedExceptionHandler(Throwable ex) {
    log.warn("[accessDeniedExceptionHandler] 权限不足: {}", ex.getMessage());
    return problemResponse(
        FORBIDDEN.getCode(),
        CommonErrorCodes.FORBIDDEN,
        String.format("没有该操作权限:%s", ex.getMessage()));
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
    return problemResponse(
        METHOD_NOT_ALLOWED.getCode(),
        CommonErrorCodes.METHOD_NOT_ALLOWED,
        String.format("请求方法不正确:%s", ex.getMessage()));
  }

  /** 处理 Resilience4j 限流抛出的异常 */
  public ResponseEntity<ApiResponse<?>> requestNotPermittedExceptionHandler(
      HttpServletRequest req, Throwable ex) {
    log.warn("[requestNotPermittedExceptionHandler][url({}) 访问过于频繁]", req.getRequestURL(), ex);
    return problemResponse(
        TOO_MANY_REQUESTS.getCode(),
        CommonErrorCodes.RATE_LIMITED,
        String.format(
            "[requestNotPermittedExceptionHandler][url(%s) 访问过于频繁]", req.getRequestURL()));
  }

  /**
   * 处理服务异常 ServiceException。
   *
   * <p>i18n 改造（方案 §6.2 / §12.3）：ServiceException 已增量新增 {@code String errorCode} 字段——handler 先透传
   * {@code ex.getErrorCode()}，旧构造路径（{@code new ServiceException(code, msg)}）errorCode 为 null 时，
   * fallback 到 {@link CommonErrorCodes#INTERNAL_ERROR}， 与此前口径一致、保持兼容。
   */
  @ExceptionHandler(value = ServiceException.class)
  public ResponseEntity<ApiResponse<?>> serviceExceptionHandler(ServiceException ex) {
    log.info("[serviceExceptionHandler]", ex);
    String errorCode =
        ex.getErrorCode() != null ? ex.getErrorCode() : CommonErrorCodes.INTERNAL_ERROR;
    return problemResponse(toHttpStatus(ex.getCode()), errorCode, ex.getMessage());
  }

  @ExceptionHandler(value = SystemException.class)
  public ResponseEntity<ApiResponse<?>> systemExceptionHandler(SystemException ex) {
    log.error("[systemExceptionHandler]", ex);
    // 2026-10-04 脱敏（P1-4）：message 只进日志，响应体用常量。
    // 原实现把 ex.getMessage() 原样回吐。这些"系统/基础"异常大多由框架层抛出，
    // message 常常是容器或驱动的原文（`Table 'x' doesn't exist` / `Column 'y' cannot be null`
    // / 约束名 / 连接池地址），等于把数据字典与拓扑交给调用方。
    // 本文件 :431 的兜底分支一直用 INTERNAL_SERVER_ERROR.getMsg() —— 脱敏能力本就存在，
    // 只是这几个分支没接上，属不一致而非缺能力。排障按 X-Trace-Id 回查日志。
    return problemResponse(
        INTERNAL_SERVER_ERROR.getCode(),
        CommonErrorCodes.INTERNAL_ERROR,
        INTERNAL_SERVER_ERROR.getMsg());
  }

  @ExceptionHandler(value = InfrastructureException.class)
  public ResponseEntity<ApiResponse<?>> infrastructureExceptionHandler(InfrastructureException ex) {
    log.error("[infrastructureExceptionHandler]", ex);
    // 同 systemExceptionHandler：这是最容易包到底层异常 message 的一类（P1-4），故一并脱敏。
    return problemResponse(
        INTERNAL_SERVER_ERROR.getCode(),
        CommonErrorCodes.INTERNAL_ERROR,
        INTERNAL_SERVER_ERROR.getMsg());
  }

  /**
   * 处理领域异常 {@link DomainException} —— 兜底「不是 500」这条底线。
   *
   * <p><b>为何必须有这个分支</b>（2026-10-07 实测）：域层抛 {@code DomainException} 是<b>正确分层</b> （{@code domain}
   * 不依赖传输层），但它<b>不带 errorCode</b>，而本 handler 此前<b>没有</b>对应分支 ⇒ 全部冒泡到 {@link
   * #defaultExceptionHandler} 兜底 ⇒ 被报成 <b>HTTP 500</b>。 实测全仓 {@code DomainException} 及其子类 {@code
   * StateConflictException} 共约 170 处 （如「账户已处于启用状态」「只有新建状态的支付单可以提交支付」），这些是<b>用户可纠正的业务错误</b>， 报500
   * 会：①误导客户端重试（重试无用，状态不会变）②把业务错误混进 5xx 错误预算、污染告警与 SLO。
   *
   * <p><b>为何默认 400 + {@code COMMON_CONFLICT} 而非逐个业务码</b>：HTTP 无法从异常本身区分
   * 「状态冲突(409)」「入参非法(400)」「权限不足(403)」，猜任一具体状态都会误导客户端 （尤其 409 语义要求客户端改状态而非重试）。<b>精确码由调用点给出</b>——
   * 推荐路径是在应用层显式翻译： {@code catch (DomainException e) → throw XxxErrors.of(XXX_CONFLICT,
   * e.getMessage())}。 本分支只负责「未被翻译的域异常至少不是 500」。
   *
   * <p><b>message 是否脱敏</b>：域异常的 message 是<b>本仓自己写的业务语义</b> （"账户已处于启用状态"），不是容器/驱动原文，<b>不脱敏</b>—— 与
   * {@code systemExceptionHandler}/{@code infrastructureExceptionHandler}（包底层异常、P1-4 脱敏）不同。
   * 若哪天域异常改为包第三方异常原文，此处需同步脱敏。
   */
  @ExceptionHandler(value = DomainException.class)
  public ResponseEntity<ApiResponse<?>> domainExceptionHandler(DomainException ex) {
    log.info("[domainExceptionHandler]", ex);
    // 400 用字面量：本文件既有分支都用 int 状态码（INTERNAL_SERVER_ERROR.getCode() 等），
    // 此处保持一致；不引入 HttpStatus 以免同一文件出现两套写法。
    return problemResponse(400, CommonErrorCodes.CONFLICT, ex.getMessage());
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
    // ★ errorCode 由异常自带（各模块 *Errors.of(errorCode, detail) 构造时写入），
    //   handler 不解析 message、也不手填常量 —— 避免"码与状态两处各写一遍"的漂移。
    //   旧构造路径（new BizException(code, msg)）errorCode 为 null，前端按回退链降级到中文 message。
    return problemResponse(httpStatus, ex.getErrorCode(), ex.getMessage());
  }

  /** 将业务错误码映射为 HTTP 状态码。 若 code 本身是合法 HTTP 状态码（400–599）则直接使用，否则默认 400。 */
  private int toHttpStatus(int code) {
    return (code >= 400 && code <= 599) ? code : 400;
  }

  /** 处理系统异常，兜底处理所有的一切 */
  @ExceptionHandler(value = Exception.class)
  public ResponseEntity<ApiResponse<?>> defaultExceptionHandler(
      HttpServletRequest req, Throwable ex) {
    // 情况零：Spring Security 鉴权失败（@PreAuthorize 未通过）。
    // 必须排在兜底逻辑最前面——否则权限不足会被翻译成 500「系统异常」，
    // 既误导排障方向，又把鉴权失败混进 5xx 错误预算。语义是 HTTP 403。
    if (isAccessDenied(ex)) {
      return accessDeniedExceptionHandler(ex);
    }

    // 情况一：处理表不存在的异常
    ApiResponse<?> tableNotExistsResult = handleTableNotExists(ex);
    if (tableNotExistsResult != null) {
      // 状态码取响应体自带的 code（404），而不是这里再写死 INTERNAL_SERVER_ERROR：
      // 此前那个 500 是配合 handleTableNotExists「命中也返回 null」的旧实现写的——
      // 它让该方法成为不可达分支，于是"表不存在"被当成"数据库连不上"一起兜底成 500，
      // 排障方向被误导到连通性上。2026-10-04（P1-3）连同方法体一起修正。
      return ResponseEntity.status(tableNotExistsResult.getCode()).body(tableNotExistsResult);
    }

    // 情况二：部分特殊的库的处理
    if (Objects.equals(
        "io.github.resilience4j.ratelimiter.RequestNotPermitted", ex.getClass().getName())) {
      return requestNotPermittedExceptionHandler(req, ex);
    }

    Throwable cause = ex.getCause();
    if (cause instanceof InvocationTargetException) {
      log.error("[handleInvocationTargetException - cause]", cause);
      // cause 多为反射调用目标的原始异常（含 SQL / 驱动 / 第三方 SDK 的 message），
      // 直出等于转述别人的异常原文（P1-4）。原始信息已完整落到日志里。
      return problemResponse(
          INTERNAL_SERVER_ERROR.getCode(),
          CommonErrorCodes.INTERNAL_ERROR,
          INTERNAL_SERVER_ERROR.getMsg());
    }

    // 情况三：处理异常
    log.error("[defaultExceptionHandler]", ex);
    // 插入异常日志
    this.createExceptionLog(req, ex);
    // 返回 ERROR ApiResponse
    return problemResponse(
        INTERNAL_SERVER_ERROR.getCode(),
        CommonErrorCodes.INTERNAL_ERROR,
        INTERNAL_SERVER_ERROR.getMsg());
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
    // 2026-10-04 修正（P1-3）：此前**命中分支也 return null** ⇒ 调用方的判空永远为真两种情况都走
    // 500 兜底，这一段是彻底的死代码。表不存在应回 404。
    // 文案只用常量：原始 message 形如 `Table 'bone_db.bone_record' doesn't exist`，
    // 直出等于把库名/表名/后续约束名交给调用方（同一问题见 P1-4）。原始信息全部留在日志里，
    // 排障走 X-Trace-Id 回查。
    log.warn("[handleTableNotExists] 目标表不存在，回 404（原始异常信息仅入日志）", ex);
    return ApiResponse.error(
        NOT_FOUND.getCode(),
        NOT_FOUND.getMsg(),
        problem(NOT_FOUND.getCode(), CommonErrorCodes.NOT_FOUND, NOT_FOUND.getMsg()));
  }

  @ExceptionHandler(SQLException.class)
  public ResponseEntity<ApiResponse<?>> handleSQLException(SQLException ex) {
    // 这是最典型的信息泄露点：SQLException.getMessage() 的原文形如
    // `Table 'bone_db.bone_record' doesn't exist`、`Duplicate entry 'x' for key 'idx_y'`
    // —— 库名、表名、索引名、约束名一并回给调用方，等于免费送一份数据字典（P1-4）。
    // 只入日志；对外一律给常量文案，排障按 X-Trace-Id 回查。
    log.error("[SQLException]", ex);
    return problemResponse(
        INTERNAL_SERVER_ERROR.getCode(),
        CommonErrorCodes.INTERNAL_ERROR,
        INTERNAL_SERVER_ERROR.getMsg());
  }
}
