package com.bone.iam.infrastructure.config;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.core.model.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * IAM 模块全局异常处理器
 *
 * <p>bone-iam 未依赖 bone-web，因此需要自行提供异常处理。 将 BizException 翻译为符合 API 规范的 HTTP 状态码 + ApiResponse 信封。 对齐
 * Bone-API-规范 §4.2 RFC 7807 ProblemDetail 结构。
 *
 * <p><b>i18n 改造（方案 §6.2 / §12.3）</b>：此前本类用手写 {@code LinkedHashMap} 拼 ProblemDetail， <strong>缺
 * {@code errorCode} 键</strong>——前端 {@code showError()} 的 errorCode 分支在 IAM 永远不命中， 英文用户只能看到中文
 * message。现统一改为 {@link ProblemDetails} 工厂产出 {@link ProblemDetail}：
 *
 * <ul>
 *   <li>业务异常透传 {@link BizException#getErrorCode()}（码随异常走，handler 不解析 message）；
 *   <li>基础设施类 handler 填 {@link CommonErrorCodes}（不新造 {@code IAM_} 前缀——这些语义任何模块都会触发， 挂模块前缀会退化成 N
 *       套同义码，破坏错误码登记 §2「可聚合」）；
 *   <li>{@code message} 仍是中文（fallback 契约），展示文案归前端语言包。
 * </ul>
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
  public ResponseEntity<ApiResponse<ProblemDetail>> handleBizException(
      BizException ex, HttpServletRequest request) {
    log.info("[handleBizException] code={}, message={}", ex.getCode(), ex.getMessage());
    int httpStatus = toHttpStatus(ex.getCode());
    return problemResponse(httpStatus, ex.getErrorCode(), ex.getMessage(), request);
  }

  /**
   * 处理领域异常（domain 层校验 / 业务规则拒绝）。
   *
   * <p>domain 抛 {@link DomainException}，由应用层翻译为带业务码的 BizException（README E-5.3.1）。
   * 本处理器是未被应用层翻译的域异常的兜底，统一返回 400，避免漏到 Exception 兜底成 500。
   */
  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleDomainException(
      DomainException ex, HttpServletRequest request) {
    log.info("[handleDomainException] message={}", ex.getMessage());
    return problemResponse(400, CommonErrorCodes.VALIDATION_FAILED, ex.getMessage(), request);
  }

  /** 处理 Spring Security 权限不足异常（@PreAuthorize 拒绝时抛出），返回 403 */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    log.warn(
        "[handleAccessDenied] 权限不足: uri={}, message={}", request.getRequestURI(), ex.getMessage());
    return problemResponse(403, CommonErrorCodes.FORBIDDEN, "无权限访问该资源", request);
  }

  /** 处理资源不存在异常（路径匹配不到任何 Controller），返回 404 */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleNoResourceFound(
      NoResourceFoundException ex, HttpServletRequest request) {
    log.warn(
        "[handleNoResourceFound] 资源不存在: uri={}, message={}",
        request.getRequestURI(),
        ex.getMessage());
    return problemResponse(404, CommonErrorCodes.NOT_FOUND, "请求的资源不存在", request);
  }

  // 405 必须显式声明：本 advice 的 Exception 兜底会抢在 bone-web 的 405 映射之前命中，
  // 否则"客户端用错方法"会被伪装成服务端 500。
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    String detail = "请求方法不被支持: " + ex.getMethod();
    log.warn(
        "[handleMethodNotSupported] uri={}, method={}", request.getRequestURI(), ex.getMethod());
    return problemResponse(405, CommonErrorCodes.METHOD_NOT_ALLOWED, detail, request);
  }

  /**
   * 处理 Controller 显式抛出的 {@link ResponseStatusException}（如 Account/Role 详情 404），按其自身状态码返回。
   *
   * <p>曾有缺陷：该异常落进 {@code Exception} 兜底被伪装成 500——跨租户 IDOR 虽被正确拒绝（内部 404）， 客户端却收到 500，既破坏 API 契约又污染
   * 5xx 告警。
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleResponseStatus(
      ResponseStatusException ex, HttpServletRequest request) {
    int status = ex.getStatusCode().value();
    String message = ex.getReason() != null ? ex.getReason() : "请求处理失败";
    String errorCode =
        switch (status) {
          case 400 -> CommonErrorCodes.VALIDATION_FAILED;
          case 403 -> CommonErrorCodes.FORBIDDEN;
          case 404 -> CommonErrorCodes.NOT_FOUND;
          case 405 -> CommonErrorCodes.METHOD_NOT_ALLOWED;
          default -> CommonErrorCodes.INTERNAL_ERROR;
        };
    log.warn(
        "[handleResponseStatus] uri={}, status={}, message={}",
        request.getRequestURI(),
        status,
        message);
    return problemResponse(status, errorCode, message, request);
  }

  /**
   * 数据库完整性约束冲突 → 400（而非被下方 catch-all 兜底成 500）。
   *
   * <p><b>2026-10-08 实测补录</b>：创建角色时 {@code name} 传超长字符串，DB 抛 {@code
   * DataIntegrityViolationException: Data truncation: Data too long for column 'name'}， 本 advice 的
   * {@code Exception} 兜底把它翻成<b>500「系统内部错误」</b>。这类失败 100% 由客户端输入 触发（字段长度是表结构的一部分），报 500
   * 会①误导调用方以为可重试②把参数笔误计入 5xx 错误预算、 污染 SLI 与告警。语义应为 <b>400 参数校验失败</b>。
   *
   * <p><b>为何不能指望 bone-web 框架 handler 兜住</b>：本 advice 与框架的 {@code GlobalExceptionHandler}
   * <b>并存</b>（{@code @ConditionalOnMissingBean(name = "globalExceptionHandler")} 只按 bean 名让路， 而本类
   * bean 名为 {@code iamExceptionHandler}），同一异常由哪个 advice 处理取决于 order ——实测 <b>本 advice
   * 优先</b>，故框架基类的同名分支对本模块<b>不生效</b>，必须在此显式声明。
   *
   * <p>响应体用常量文案、原始异常只入日志：{@code ex.getMessage()} 含完整 SQL 与列名 （{@code Data too long for column
   * 'name'}），回给调用方等于泄露表结构（同框架P1-4脱敏口径）。
   */
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleDataIntegrityViolation(
      org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
    log.warn(
        "[handleDataIntegrityViolation] 数据完整性约束冲突: uri={}, message={}",
        request.getRequestURI(),
        ex.getMessage());
    return problemResponse(400, CommonErrorCodes.VALIDATION_FAILED, "数据违反完整性约束，请检查提交字段", request);
  }

  /** 兜底处理所有未捕获异常 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleException(
      Exception ex, HttpServletRequest request) {
    log.error(
        "[handleException] 未预期异常: type={}, message={}",
        ex.getClass().getName(),
        ex.getMessage(),
        ex);
    return problemResponse(500, CommonErrorCodes.INTERNAL_ERROR, "系统内部错误", request);
  }

  /** 处理请求体解析异常（JSON 格式错误、类型不匹配等） */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    log.warn("[handleHttpMessageNotReadable] {}", ex.getMessage());
    return problemResponse(400, CommonErrorCodes.MALFORMED_REQUEST, "请求体格式错误", request);
  }

  /**
   * 处理请求参数类型不匹配（路径变量/查询参数无法转成目标类型）。
   *
   * <p><b>为何必须显式接管</b>：{@code @PathVariable Long id} 收到 {@code "abc"} 时抛 {@link
   * MethodArgumentTypeMismatchException}，它既非 {@link BizException} 也非校验异常， 此前一路冒泡到
   * {@code @ExceptionHandler(Exception.class)} 兜底分支，被翻译成 <b>500 COMMON_INTERNAL_ERROR</b>。 后果：客户端
   * 传错一个参数就被告知「服务器内部出错」，排障方向被带偏；且这类 100% 由外部输入触发的错误会计入 5xx 错误·预算，掩盖真实的服务端故障——与「权限失败不该混进错误预算」是同一类问题。
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    log.warn(
        "[handleTypeMismatch] 参数类型不匹配: name={}, value={}, requiredType={}",
        ex.getName(),
        ex.getValue(),
        ex.getRequiredType());
    return problemResponse(
        400,
        CommonErrorCodes.MALFORMED_REQUEST,
        String.format("参数 %s 格式不正确", ex.getName()),
        request);
  }

  /** 处理参数校验异常（@NotBlank 等） */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ProblemDetail.FieldError> errors = toFieldErrors(ex);
    String message =
        errors.stream()
            .map(e -> e.getField() + ": " + e.getMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("参数校验失败");
    log.warn("[handleValidation] {}", message);
    // 结构化字段错误一并给出：前端可在英文界面逐字段展示，而不是把一整串中文 detail 丢给用户
    return problemResponseWithErrors(
        400, CommonErrorCodes.VALIDATION_FAILED, message, errors, request);
  }

  // ===== 统一出口 =====

  private ResponseEntity<ApiResponse<ProblemDetail>> problemResponse(
      int status, String errorCode, String message, HttpServletRequest request) {
    return problemResponseWithErrors(status, errorCode, message, null, request);
  }

  private ResponseEntity<ApiResponse<ProblemDetail>> problemResponseWithErrors(
      int status,
      String errorCode,
      String message,
      List<ProblemDetail.FieldError> errors,
      HttpServletRequest request) {
    ProblemDetail problem = ProblemDetails.of(errorCode, status, message, uriOf(request));
    if (errors != null && !errors.isEmpty()) {
      problem.setErrors(errors);
    }
    return ResponseEntity.status(status).body(ApiResponse.error(status, message, problem));
  }

  private static String uriOf(HttpServletRequest request) {
    return request == null ? null : request.getRequestURI();
  }

  private static List<ProblemDetail.FieldError> toFieldErrors(MethodArgumentNotValidException ex) {
    List<ProblemDetail.FieldError> errors = new ArrayList<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      ProblemDetail.FieldError error = new ProblemDetail.FieldError();
      error.setField(fe.getField());
      error.setMessage(fe.getDefaultMessage());
      error.setRejectedValue(fe.getRejectedValue());
      errors.add(error);
    }
    return errors;
  }

  /** 将业务错误码映射为 HTTP 状态码。 若 code 本身是合法 HTTP 状态码（400–599）则直接使用，否则默认 400。 */
  private int toHttpStatus(int code) {
    return (code >= 400 && code <= 599) ? code : 400;
  }
}
