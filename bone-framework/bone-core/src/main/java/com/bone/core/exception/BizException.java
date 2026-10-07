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
 * <p><b>为何要带 errorCode</b>：不带码时前端只能把中文{@code message} 直接展示给用户（英文用户看到的仍是中文）， 且监控/告警只能按中文文案聚合。带码后
 * {@code ProblemDetail.errorCode} 成为前端 {@code i18n.t('errors.' + errorCode)} 的键（《国际化设计方案》§3 回退链）。
 *
 * <p><b>⚠️ 无码构造器/工厂已弃用（2026-10-07）</b>：本类原9 个静态 {@code of()} 重载**无一携带 errorCode** —— 它们统统委派到把
 * {@code errorCode} 硬置 {@code null} 的 {@code (int,String,Throwable)} / {@code (String)}
 * 构造器；连注释标注为「使用枚举类（推荐）」的 {@code of(ErrorCode)} 也一样（{@code ErrorCode} 只有 {@code code}+{@code
 * msg}，委派后码仍是 null）。 于是 {@code ProblemDetail.errorCode} 恒为空 ⇒ 前端拿不到 i18n 键、监控只能按文案聚合。
 *
 * <p>正确用法：走本模块 {@code {Module}Errors.of({Module}ErrorCodes.XXX, 上下文[, cause])}
 * （唯一真源，未登记状态即类加载失败）。门禁 {@code scripts/check-error-code-landing.py} 已阻断 {@code application/} 与
 * {@code adapter/web/} 内的无码抛出。
 *
 * <p>保留这些重载仅为二进制兼容与infra/domain 层的历史调用，<b>请勿在新代码使用</b>。
 *
 * @see <a href="doc/architecture/Bone-错误码登记.md">Bone-错误码登记</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BizException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** 默认错误码，可根据需要自行调整 */
  public static final int DEFAULT_ERROR_CODE = 500;

  /** 业务错误码（HTTP 状态语义，int） */
  private final int code;

  /** 稳定业务码字符串（i18n / 聚合契约）；无码构造路径为 null */
  private final String errorCode;

  // ===== 无码构造器（已弃用，勿在新代码使用）=====
  // 保留仅为兼容存量调用；它们把 errorCode 恒置 null ⇒ ProblemDetail.errorCode 为空
  // ⇒ 前端拿不到 i18n 键、监控只能按文案聚合。
  // 替代：走 {Module}Errors.of({Module}ErrorCodes.XXX, 上下文[, cause])。

  /**
   * @deprecated 不携带 errorCode。改用 {@code {Module}Errors.of(码, 上下文[, cause])}。
   */
  @Deprecated
  public BizException(int code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.errorCode = null;
  }

  /**
   * @deprecated 不携带 errorCode（且状态恒为 500）。改用 {@code {Module}Errors.of(码, 上下文)}。
   */
  @Deprecated
  public BizException(String message, Throwable cause) {
    super(message, cause);
    this.code = DEFAULT_ERROR_CODE;
    this.errorCode = null;
  }

  /**
   * @deprecated 不携带 errorCode。改用 {@code {Module}Errors.of(码, 上下文)}。
   */
  @Deprecated
  public BizException(int code, String message) {
    super(message);
    this.code = code;
    this.errorCode = null;
  }

  /**
   * @deprecated 不携带 errorCode（且状态恒为 500，404/400 会被误报成服务端故障）。改用 {@code {Module}Errors.of(码, 上下文)}。
   */
  @Deprecated
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

  // ===== 静态工厂方法区域=====
  //
  // ⚠️ 以下 6 个 of() 均**不携带 errorCode**（委派到无码构造器），已弃用；
  //   保留仅为兼容存量调用。请改走 {Module}Errors.of({Module}ErrorCodes.XXX, 上下文[, cause])。
  //    门禁 scripts/check-error-code-landing.py 阻断 application/ 与 adapter/web/ 内的无码抛出。

  /**
   * 使用默认错误码 & 自定义错误消息
   *
   * @deprecated 不携带 errorCode。改用 {@code {Module}Errors.of(码, 上下文)}。
   */
  @Deprecated
  public static BizException of(String message) {
    // (Throwable) 强转不可省：null 同时匹配 (int,String,Throwable) 与 (int,String,String)，不转会编译歧义
    return new BizException(DEFAULT_ERROR_CODE, message, (Throwable) null);
  }

  /**
   * 使用默认错误码 & 自定义错误消息 & 传递 cause
   *
   * @deprecated 不携带 errorCode。改用 {@code {Module}Errors.of(码, 上下文, cause)}。
   */
  @Deprecated
  public static BizException of(String message, Throwable cause) {
    return new BizException(DEFAULT_ERROR_CODE, message, cause);
  }

  /**
   * 使用自定义错误码 & 错误消息
   *
   * @deprecated 不携带 errorCode。改用 {@code {Module}Errors.of(码, 上下文)}。
   */
  @Deprecated
  public static BizException of(int code, String message) {
    return new BizException(code, message, (Throwable) null);
  }

  /**
   * 使用自定义错误码 & 错误消息 & 传递 cause
   *
   * @deprecated 不携带 errorCode。改用 {@code {Module}Errors.of(码, 上下文, cause)}。
   */
  @Deprecated
  public static BizException of(int code, String message, Throwable cause) {
    return new BizException(code, message, cause);
  }

  /**
   * 使用枚举类（推荐）
   *
   * @deprecated<javadoc>⚠️ <b>该重载同样不携带 errorCode</b>：{@code ErrorCode} 只有 {@code code}+{@code msg}，
   *     委派到无码构造器后 {@code getErrorCode()} 为 null ⇒ ProblemDetail.errorCode 为空。 真正需要「码随异常走」时，请用
   *     {@code {Module}Errors.of(码, 上下文, cause)} （码直接写进 {@link #getErrorCode()}，前端 i18n
   *     映射与监控聚合才有键）。 </javadoc>
   */
  @Deprecated
  public static BizException of(ErrorCode errorCode) {
    return new BizException(errorCode.getCode(), errorCode.getMsg(), (Throwable) null);
  }

  /**
   * 使用枚举类并传递 cause
   *
   * @deprecated 同 {@link #of(ErrorCode)}，不携带 errorCode。改用 {@code {Module}Errors.of(码, 上下文, cause)}。
   */
  @Deprecated
  public static BizException of(ErrorCode errorCode, Throwable cause) {
    return new BizException(errorCode.getCode(), errorCode.getMsg(), cause);
  }
}
