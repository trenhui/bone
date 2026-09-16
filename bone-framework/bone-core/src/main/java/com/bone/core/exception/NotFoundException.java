package com.bone.core.exception;

import java.io.Serial;

/**
 * 资源未找到异常。固定映射 HTTP <strong>404</strong>。
 *
 * <p><b>为什么显式给状态码</b>：{@code BizException(String)} 的默认码是 <strong>500</strong>，而 {@code
 * GlobalExceptionHandler} 用 code 决定 HTTP 状态（400–599 直接用，其余兜底 400）。若沿用默认值，「资源不存在」会被 报成 5xx
 * 服务端故障——既误导排障，也会污染 5xx 告警与 SLO 口径（API 规范 §2.4、错误码登记 §7 均要求 404）。
 */
public class NotFoundException extends BizException {

  /** 资源不存在对应的 HTTP 状态码。 */
  private static final int NOT_FOUND_STATUS = 404;

  @Serial private static final long serialVersionUID = 1L;

  public NotFoundException(String message) {
    super(NOT_FOUND_STATUS, message);
  }

  public static NotFoundException of(String message) {
    return new NotFoundException(message);
  }
}
