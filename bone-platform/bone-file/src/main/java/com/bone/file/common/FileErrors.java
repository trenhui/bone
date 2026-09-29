package com.bone.file.common;

import com.bone.core.exception.BizException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * 业务错误码 → HTTP 状态 的<strong>唯一配对真源</strong>，以及抛出 {@link BizException} 的统一工厂。
 *
 * <p><b>为何需要它</b>：{@code BizException} 首参是 <em>HTTP 状态</em>，业务码走独立的 {@code errorCode}
 * 字段——两者语义不同、天然两份数据。若在每个抛出点手写 {@code new BizException(404, NOT_FOUND + ": " + name,
 * NOT_FOUND)}，配对散落全模块，任一处不一致都没有机制发现（错误码登记 §17 禁止把 HTTP 码与业务码混为同一整数）。
 *
 * <p><b>新增错误码的强制约束</b>：{@link FileErrorCodes} 的每个 public String 常量都必须在表中登记状态， 否则类加载即抛 {@link
 * IllegalStateException}——漏登记不可能溜到运行期，也不会被兜底成 400/500。
 */
public final class FileErrors {

  /** 码 → 默认 HTTP 状态（真源）；{@link #checkEveryCodeRegistered()} 保证不会漏。 */
  private static final Map<String, Integer> DEFAULT_HTTP_STATUS =
      Map.ofEntries(
          Map.entry(FileErrorCodes.UPLOAD_FAILED, 500),
          Map.entry(FileErrorCodes.DOWNLOAD_FAILED, 500),
          Map.entry(FileErrorCodes.DELETE_FAILED, 500),
          Map.entry(FileErrorCodes.NOT_FOUND, 404),
          Map.entry(FileErrorCodes.NAME_INVALID, 400),
          Map.entry(FileErrorCodes.TYPE_NOT_ALLOWED, 400),
          Map.entry(FileErrorCodes.TENANT_CONTEXT_MISSING, 400),
          Map.entry(FileErrorCodes.ACCESS_DENIED, 403));

  static {
    checkEveryCodeRegistered();
  }

  private FileErrors() {}

  /** 抛业务异常，不带附加上下文。 */
  public static BizException of(String errorCode) {
    return of(errorCode, null, null);
  }

  /** 抛业务异常，附业务标识或上下文说明（拼成 {@code 码: 说明}）。 */
  public static BizException of(String errorCode, Object detail) {
    return of(errorCode, detail, null);
  }

  /**
   * 抛业务异常，附上下文说明与根因。
   *
   * <p><b>必须用四参构造</b>：{@code new BizException(status, message, cause)} 会把 {@code errorCode} 置
   * {@code null}，导致 {@code ProblemDetail} 拿不到码、前端 {@code i18n.t('errors.' + errorCode)} 的分支
   * 永不命中（英文用户只能看到中文 message）。这里把 {@code errorCode} 作为独立字段写入，与 {@link #DEFAULT_HTTP_STATUS}
   * 的状态配对一并成为真源。
   *
   * <p>{@code message} 保留 {@code 码: 说明} 形态，作为 errorCode 缺失时的展示 fallback 与日志聚合兜底。
   */
  public static BizException of(String errorCode, Object detail, Throwable cause) {
    return new BizException(
        httpStatusOf(errorCode), composeMessage(errorCode, detail), errorCode, cause);
  }

  /** 取错误码的默认 HTTP 状态；未登记即抛——本类核心不变量，不做兜底。 */
  public static int httpStatusOf(String errorCode) {
    Integer status = DEFAULT_HTTP_STATUS.get(errorCode);
    if (status == null) {
      throw new IllegalStateException(
          "错误码未登记默认 HTTP 状态: "
              + errorCode
              + "；请在 FileErrors 的 DEFAULT_HTTP_STATUS 中登记，"
              + "否则该异常会被兜底成 400。");
    }
    return status;
  }

  private static String composeMessage(String errorCode, Object detail) {
    return detail == null ? errorCode : errorCode + ": " + detail;
  }

  /**
   * 校验 {@link FileErrorCodes} 的每个码常量都在状态表中。
   *
   * <p>用反射而非人工清单：人工清单本身就是又一份需要同步的数据，与「消除配对漂移」的初衷相悖。
   */
  private static void checkEveryCodeRegistered() {
    for (Field field : FileErrorCodes.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
        continue;
      }
      final String code;
      try {
        code = (String) field.get(null);
      } catch (IllegalAccessException ex) {
        throw new IllegalStateException("无法读取错误码常量: " + field.getName(), ex);
      }
      if (!DEFAULT_HTTP_STATUS.containsKey(code)) {
        throw new IllegalStateException(
            "错误码常量未在 FileErrors 登记默认 HTTP 状态: "
                + "FileErrorCodes."
                + field.getName()
                + " = \""
                + code
                + "\"。新增错误码必须同步登记状态（错误码登记 §7）。");
      }
    }
  }
}
