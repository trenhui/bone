package com.bone.integration.adapter.web.advice;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.core.model.ProblemDetails;
import com.bone.integration.common.exception.NotFoundException;
import com.bone.integration.common.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 集成模块异常映射：未实现连接器返回 HTTP 501，避免假成功（INT-01）。
 *
 * <p><b>i18n 改造（方案 §6.2 / §12.3）</b>：此前全部返回 {@code ApiResponse<Void>}（{@code data} 为 {@code null}），
 * 前端拿不到 {@code errorCode}；「未实现」这类语义还是靠 {@code "INT_CONNECTOR_NOT_IMPLEMENTED: "} 前缀拼在 message
 * 里（文案一改，聚合口径就断）。现统一产出 {@link ProblemDetail}：语义落到 {@code errorCode}， message 只作中文 fallback。
 */
/**
 * 与框架 {@code GlobalExceptionHandler}（经 bone-web 自动配置注册）共存，本 advice <strong>优先</strong>。
 *
 * <p><b>为何必须显式声明顺序</b>：两个 {@code @RestControllerAdvice} 都处理 {@code BizException}，若都不带
 * {@code @Order}，Spring 的排序在二者之间是不确定的——同一个 {@code INT_FLOW_NOT_FOUND} 可能这次走模块分支（404）、 下次走框架分支（取决于
 * Bean 注册顺序）。本模块有框架没有的特有映射（连接器未实现 → 501），必须稳定胜出； 框架处理器则兜底本 advice 未覆盖的框架级异常（参数绑定 / 类型不匹配 / 405 等）。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class IntegrationExceptionAdvice {

  private static final int NOT_IMPLEMENTED_CODE = 501;

  /** 未实现 → 501。码用 {@code COMMON_NOT_IMPLEMENTED}：不再是 message 前缀，而是可聚合的稳定码。 */
  @ExceptionHandler(UnsupportedOperationException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> notImplemented(
      UnsupportedOperationException ex) {
    String message = "连接器能力未实现: " + ex.getMessage();
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .body(problem(NOT_IMPLEMENTED_CODE, CommonErrorCodes.NOT_IMPLEMENTED, message));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> notFound(NotFoundException ex) {
    log.warn("Not found: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(problem(404, CommonErrorCodes.NOT_FOUND, ex.getMessage()));
  }

  @ExceptionHandler(SystemException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> systemException(SystemException ex) {
    log.error("System exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(problem(500, CommonErrorCodes.INTERNAL_ERROR, ex.getMessage()));
  }

  /**
   * 数据库完整性约束冲突 → 400。
   *
   * <p><b>2026-10-08 实测补录</b>：写入连接器/集成实体时若字段超长或违反非空约束，DB 抛 {@code
   * DataIntegrityViolationException}。本 advice 带 {@code @Order(HIGHEST_PRECEDENCE)}、 <b>优先于 bone-web
   * 框架基类</b>，且本类此前<b>没有</b> {@code Exception} 之外的通用兜底之外的分支 （该异常此前直接冒泡到框架基类的 {@code Exception} 兜底）⇒
   * 框架基类里的同名分支对本模块<b>不生效</b>， 必须在此显式声明，否则仍会被翻成 500「系统内部错误」，把客户端参数错误计入 5xx 错误预算。
   *
   * <p>响应体用常量文案、原始异常只入日志（{@code ex.getMessage()} 含完整 SQL 与列名，回显等于泄露表结构）。
   */
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> dataIntegrityViolation(
      org.springframework.dao.DataIntegrityViolationException ex) {
    log.warn("数据完整性约束冲突: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(problem(400, CommonErrorCodes.VALIDATION_FAILED, "数据违反完整性约束，请检查提交字段"));
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> domainException(DomainException ex) {
    log.warn("Domain exception: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(problem(400, CommonErrorCodes.VALIDATION_FAILED, ex.getMessage()));
  }

  /**
   * 业务异常 → HTTP 状态 + {@code errorCode}。
   *
   * <p><b>为何不再「非 501 一律压成 400」</b>：{@link IntegrationErrors} 的「码 → 状态」表已给出 404 / 409 / 501
   * 等语义（错误码登记 §6 {@code INT_} 段），若此处仍只保留 501 一个分支，{@code INT_FLOW_NOT_FOUND} 会以 HTTP 400
   * 返回——前端按状态分流会把「不存在」误判成「参数非法」，与 bone-web / bone-system 的「HTTP 状态码与 {@code ApiResponse.code}
   * 对齐」契约相悖。故改为 400–599 直用、否则兜底 400。
   */
  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> bizException(BizException ex) {
    int httpStatus = toHttpStatus(ex.getCode());
    log.warn("Biz exception [{}] {}: {}", httpStatus, ex.getErrorCode(), ex.getMessage());
    String errorCode =
        ex.getErrorCode() != null ? ex.getErrorCode() : CommonErrorCodes.VALIDATION_FAILED;
    return ResponseEntity.status(httpStatus).body(problem(httpStatus, errorCode, ex.getMessage()));
  }

  /** 与 bone-web / bone-system 同一口径：{@code code} 是合法 HTTP 状态（400–599）则直用，否则兜底 400。 */
  private static int toHttpStatus(int code) {
    return (code >= 400 && code <= 599) ? code : HttpStatus.BAD_REQUEST.value();
  }

  private static ApiResponse<ProblemDetail> problem(int status, String errorCode, String message) {
    return ApiResponse.error(status, message, ProblemDetails.of(errorCode, status, message));
  }
}
