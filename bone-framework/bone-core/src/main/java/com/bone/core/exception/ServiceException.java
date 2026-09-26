package com.bone.core.exception;

import com.bone.core.enums.ErrorCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务逻辑异常（Service 层抛出）。
 *
 * <p><b>与 {@link BizException} 的关系</b>：两个并列的业务异常类——{@code BizException} 是 DDD 分层里的应用/领域层契约， {@code
 * ServiceException} 是旧的 service 层遗留实现。两者都带 {@code int code}（HTTP 状态码，400–599 直用，否则兜底 400）。
 *
 * <p><b>i18n 改造（方案 §6.2 / §12.3）</b>：增量新增 {@code String errorCode}（稳定业务码，跨系统契约与前端 {@code
 * i18n.t('errors.' + errorCode)} 的键）。 旧构造路径 {@code new ServiceException(code, msg)} / {@code new
 * ServiceException(errorCodeEnum)} errorCode 为 null，handler 按回退链降级到中文 {@code
 * COMMON_INTERNAL_ERROR}。 新增的 {@link #ServiceException(Integer, String, String)} 允许同时带 HTTP 状态码 +
 * 中文 message + 稳定业务码。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public final class ServiceException extends RuntimeException {

  /** HTTP 状态码（400–599 直用，否则兜底 400）。 */
  private Integer code;

  /** 错误提示（中文 fallback，展示文案归前端语言包）。 */
  private String message;

  /**
   * 稳定业务码（如 {@code COMMON_INTERNAL_ERROR}、{@code EXT_STATE_INVALID}）。
   *
   * <p>跨系统契约与前端 {@code i18n.t('errors.' + errorCode)} 的键——是监控/告警按码聚合的依据。
   */
  private String errorCode;

  /** 空构造方法，避免反序列化问题。 */
  public ServiceException() {}

  /** 仅带 HTTP 状态码 + 中文 message（errorCode 为 null，handler 归到 COMMON_INTERNAL_ERROR）。 */
  public ServiceException(ErrorCode errorCode) {
    this.code = errorCode.getCode();
    this.message = errorCode.getMsg();
  }

  /** 仅带 HTTP 状态码 + 中文 message（errorCode 为 null，handler 归到 COMMON_INTERNAL_ERROR）。 */
  public ServiceException(Integer code, String message) {
    this.code = code;
    this.message = message;
  }

  /** 完整构造：HTTP 状态码 + 中文 message + 稳定业务码。 */
  public ServiceException(Integer code, String message, String errorCode) {
    super(message);
    this.code = code;
    this.message = message;
    this.errorCode = errorCode;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
