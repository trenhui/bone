package com.bone.system.common;

import com.bone.core.exception.BizException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 业务错误码 → HTTP 状态 的<strong>唯一配对真源</strong>，以及抛出 {@link BizException} 的统一工厂。
 *
 * <p><b>为何需要它</b>：{@code BizException} 的第一个参数是 <em>HTTP 状态</em>（{@code GlobalExceptionHandler}
 * 用它设置响应状态），而<em>业务码</em>只能以字符串拼进 message——于是「码」与「状态」天然是两份数据。若在每个抛出点手写 {@code new BizException(404,
 * CONFIG_NOT_FOUND + ": " + id)}，配对就散落在全模块几十处，任一处不一致都没有机制发现 （错误码登记 §17 明文「禁止把 HTTP 码与业务码混为同一个整数」）。
 *
 * <p><b>为什么不用「每种失败一个自定义异常 + 一个 {@code @ExceptionHandler}」</b>：那样每加一种失败就要多一组 「异常类 + handler 映射」，且日志
 * / 告警只能按中文 message 分类。本类把配对收回一张表，抛出点只表达 <strong>业务语义</strong>（哪个码 + 什么上下文），状态由 {@link
 * #DEFAULT_HTTP_STATUS} 提供。
 *
 * <p><b>新增错误码的强制约束</b>：{@link SystemErrorCodes} 的每个 public String 常量都必须在表中登记状态， 否则类加载即抛 {@link
 * IllegalStateException}——漏登记不可能溜到运行期（fail fast，而不是被兜底成 400/500）。
 */
public final class SystemErrors {

  /** 码 → 默认 HTTP 状态（真源）；{@link #checkEveryCodeRegistered()} 保证不会漏。 */
  private static final Map<String, Integer> DEFAULT_HTTP_STATUS =
      Map.ofEntries(
          Map.entry(SystemErrorCodes.CONFIG_NOT_FOUND, 404),
          Map.entry(SystemErrorCodes.CONFIG_KEY_CONFLICT, 409),
          Map.entry(SystemErrorCodes.CONFIG_TYPE_INVALID, 400),
          Map.entry(SystemErrorCodes.CONFIG_KEY_INVALID, 400),
          Map.entry(SystemErrorCodes.CONFIG_SNAPSHOT_EMPTY, 400),
          Map.entry(SystemErrorCodes.CONFIG_SNAPSHOT_INVALID, 400),
          Map.entry(SystemErrorCodes.CONFIG_SNAPSHOT_TOO_LARGE, 400),
          Map.entry(SystemErrorCodes.CONFIG_SNAPSHOT_READ_FAILED, 400),
          Map.entry(SystemErrorCodes.DICT_NOT_FOUND, 404),
          Map.entry(SystemErrorCodes.DICT_CODE_CONFLICT, 409),
          Map.entry(SystemErrorCodes.DICT_TYPE_INVALID, 400),
          Map.entry(SystemErrorCodes.ALERT_RULE_NOT_FOUND, 404),
          Map.entry(SystemErrorCodes.ALERT_RECORD_NOT_FOUND, 404),
          Map.entry(SystemErrorCodes.ALERT_LEVEL_INVALID, 400),
          Map.entry(SystemErrorCodes.ALERT_RULE_INVALID, 400),
          Map.entry(SystemErrorCodes.ALERT_STATUS_INVALID, 400),
          Map.entry(SystemErrorCodes.SCHEDULE_TASK_NOT_FOUND, 404),
          Map.entry(SystemErrorCodes.SCHEDULE_TASK_STATUS_INVALID, 400),
          Map.entry(SystemErrorCodes.SCHEDULE_TASK_HANDLER_NOT_FOUND, 404),
          Map.entry(SystemErrorCodes.SCHEDULE_TASK_RUN_FAILED, 500),
          Map.entry(SystemErrorCodes.LOG_NOT_FOUND, 404),
          Map.entry(SystemErrorCodes.LOG_LEVEL_INVALID, 400));

  static {
    checkEveryCodeRegistered();
  }

  private SystemErrors() {}

  /** 抛业务异常，不带附加上下文。 */
  public static BizException of(String errorCode) {
    return of(errorCode, null, null);
  }

  /** 抛业务异常，附业务标识或上下文说明（拼成 {@code 码: 说明}）。 */
  public static BizException of(String errorCode, Object detail) {
    return of(errorCode, detail, null);
  }

  /** 抛业务异常，附上下文说明与根因。 */
  public static BizException of(String errorCode, Object detail, Throwable cause) {
    return new BizException(httpStatusOf(errorCode), composeMessage(errorCode, detail), cause);
  }

  /**
   * 供 {@code Optional.orElseThrow(...)} 使用的延迟构造器。
   *
   * <p>用 Supplier 而非直接构造异常，是为了让「聚合不存在」这类正常分支<b>不付出构造开销</b>（含堆栈填充）。
   */
  public static Supplier<BizException> supplier(String errorCode, Object detail) {
    return () -> of(errorCode, detail);
  }

  /** 取错误码的默认 HTTP 状态；未登记即抛——这是本类的核心不变量，不做兜底。 */
  public static int httpStatusOf(String errorCode) {
    Integer status = DEFAULT_HTTP_STATUS.get(errorCode);
    if (status == null) {
      throw new IllegalStateException(
          "错误码未登记默认 HTTP 状态: "
              + errorCode
              + "；请在 SystemErrors 的 DEFAULT_HTTP_STATUS 中登记，"
              + "否则该异常会被 GlobalExceptionHandler 兜底成 400。");
    }
    return status;
  }

  private static String composeMessage(String errorCode, Object detail) {
    return detail == null ? errorCode : errorCode + ": " + detail;
  }

  /**
   * 校验 {@link SystemErrorCodes} 的每个码常量都在状态表中。
   *
   * <p>用反射而非人工清单：人工清单本身就是又一份需要同步的数据，与「消除配对漂移」的初衷相悖。
   */
  private static void checkEveryCodeRegistered() {
    for (Field field : SystemErrorCodes.class.getDeclaredFields()) {
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
            "错误码常量未在 SystemErrors 登记默认 HTTP 状态: "
                + "SystemErrorCodes."
                + field.getName()
                + " = \""
                + code
                + "\"。新增错误码必须同步登记状态（错误码登记 §7）。");
      }
    }
  }
}
