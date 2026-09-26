package com.bone.core.exception;

import com.bone.core.enums.ErrorCode;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务异常：专门用于抛出明确的业务错误，如参数非法、状态冲突等
 *
 * <p><b>两个「码」不要混淆</b>（i18n 方案 §6.2.1）：
 *
 * <ul>
 *   <li>{@link #getCode()} —— <b>int</b>，HTTP 状态语义，供 {@code GlobalExceptionHandler} 设置响应状态码；
 *   <li>{@link #getErrorCode()} —— <b>String</b>，稳定业务码（如 {@code BP_ORDER_NOT_FOUND}）， 是跨系统契约与前端
 *       i18n 映射的键。旧构造路径为 {@code null}。
 * </ul>
 *
 * 二者语义不同，不可合并为同一字段：把 {@code code} 改成 String 会打断所有按 int 比较的调用点。
 *
 * <p><b>为何要带 errorCode</b>：不带码时前端只能把中文 {@code message} 直接展示给用户（英文用户看到的仍是中文）， 且监控/告警只能按中文文案聚合。带码后
 * {@code ProblemDetail.errorCode} 成为前端 {@code i18n.t('errors.' + errorCode)} 的键（《国际化设计方案》§3 回退链）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BizException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** 默认错误码，可根据需要自行调整 */
  public static final int DEFAULT_ERROR_CODE = 500;

  /** 业务错误码（HTTP 状态语义，int） */
  private final int code;

  /** 稳定业务码字符串（i18n / 聚合契约）；旧构造路径为 null */
  private final String errorCode;

  /** 建议只从静态工厂方法调用 */
  public BizException(int code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.errorCode = null;
  }

  /** 建议只从静态工厂方法调用 */
  public BizException(String message, Throwable cause) {
    super(message, cause);
    this.code = DEFAULT_ERROR_CODE;
    this.errorCode = null;
  }

  /** 建议只从静态工厂方法调用 */
  public BizException(int code, String message) {
    super(message);
    this.code = code;
    this.errorCode = null;
  }

  /** 建议只从静态工厂方法调用 */
  public BizException(String message) {
    super(message);
    this.code = DEFAULT_ERROR_CODE;
    this.errorCode = null;
  }

  /** 携带稳定业务码（i18n 场景推荐）。 */
  public BizException(int code, String message, String errorCode) {
    super(message);
    this.code = code;
    this.errorCode = errorCode;
  }

  /** 携带稳定业务码 + 根因（i18n 场景推荐）。 */
  public BizException(int code, String message, String errorCode, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.errorCode = errorCode;
  }

  // ===== 静态工厂方法区域 =====

  /** 使用默认错误码 & 自定义错误消息 */
  public static BizException of(String message) {
    // (Throwable) 强转不可省：null 同时匹配 (int,String,Throwable) 与新增的 (int,String,String)，不转会编译歧义
    return new BizException(DEFAULT_ERROR_CODE, message, (Throwable) null);
  }

  /** 使用默认错误码 & 自定义错误消息 & 传递 cause */
  public static BizException of(String message, Throwable cause) {
    return new BizException(DEFAULT_ERROR_CODE, message, cause);
  }

  /** 使用自定义错误码 & 错误消息 */
  public static BizException of(int code, String message) {
    return new BizException(code, message, (Throwable) null);
  }

  /** 使用自定义错误码 & 错误消息 & 传递 cause */
  public static BizException of(int code, String message, Throwable cause) {
    return new BizException(code, message, cause);
  }

  /** 使用枚举类（推荐），如 ResultCode */
  public static BizException of(ErrorCode errorCode) {
    return new BizException(errorCode.getCode(), errorCode.getMsg(), (Throwable) null);
  }

  /** 使用枚举类并传递 cause */
  public static BizException of(ErrorCode errorCode, Throwable cause) {
    return new BizException(errorCode.getCode(), errorCode.getMsg(), cause);
  }
}
