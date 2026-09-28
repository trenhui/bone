package com.bone.engine.extension.studio.common;

import com.bone.core.exception.BizException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 「业务错误码 → HTTP 状态」的<strong>唯一配对真源</strong>，以及抛出 {@link BizException} 的统一工厂。
 *
 * <p><b>为何需要它</b>：{@link BizException} 的第一个参数是 <em>HTTP 状态</em>（响应状态码），业务码走独立的 {@code errorCode}
 * 字段——两者语义不同、天然是两份数据（错误码登记 §17 明文禁止把两者混为同一个整数）。若在每个 抛出点手写 {@code new BizException(404, "插件不存在: " +
 * id)}，配对就散落在全模块几十处，任一处不一致都没有机制发现。
 *
 * <p><b>为什么不用「每种失败一个自定义异常 + 一个 {@code @ExceptionHandler}」</b>：那样每加一种失败就要多一组「异常类 + handler
 * 映射」，且日志/告警只能按中文 message 分类。本类把配对收回一张表，抛出点只表达<strong>业务语义</strong>（哪个码 + 什么上下文），状态由 {@link
 * #DEFAULT_HTTP_STATUS} 提供。
 *
 * <p><b>新增错误码的强制约束</b>：{@link StudioErrorCodes} 的每个 {@code String} 常量都必须在表中登记状态，否则类加载即 抛 {@link
 * IllegalStateException}（fail fast）——不会等到线上才被兜底成 400/500。
 *
 * <p><b>与 Studio 的既有异常的边界</b>：参数校验类仍可抛 {@code IllegalArgumentException}（由 {@code
 * StudioWebExceptionHandler} 映射为 400 + {@code COMMON_VALIDATION_FAILED}）；本工厂用于「资源不存在 /
 * 状态冲突」这类<em>必须带语义码</em>的失败。
 */
public final class StudioErrors {

  /** 码 → 默认 HTTP 状态（真源）；{@link #checkEveryCodeRegistered()} 保证不会漏。 */
  private static final Map<String, Integer> DEFAULT_HTTP_STATUS =
      Map.ofEntries(
          Map.entry(StudioErrorCodes.PLUGIN_NOT_FOUND, 404),
          Map.entry(StudioErrorCodes.EXT_POINT_NOT_FOUND, 404),
          Map.entry(StudioErrorCodes.PLUGIN_VERSION_NOT_FOUND, 404),
          Map.entry(StudioErrorCodes.PLUGIN_VERSION_CONFLICT, 409),
          Map.entry(StudioErrorCodes.DEPLOY_STATE_INVALID, 409),
          Map.entry(StudioErrorCodes.PLUGIN_PACKAGE_INVALID, 400),
          Map.entry(StudioErrorCodes.RESOURCE_NOT_FOUND, 404),
          Map.entry(StudioErrorCodes.STATE_INVALID, 409),
          Map.entry(StudioErrorCodes.VALIDATION_FAILED, 400),
          Map.entry(StudioErrorCodes.FORBIDDEN, 403),
          Map.entry(StudioErrorCodes.INTERNAL_ERROR, 500),
          Map.entry(StudioErrorCodes.IDEMPOTENCY_CONFLICT, 409),
          Map.entry(StudioErrorCodes.PRECONDITION_FAILED, 412));

  static {
    checkEveryCodeRegistered();
  }

  private StudioErrors() {}

  /** 抛业务异常，附业务标识或上下文说明（拼成 {@code 码: 说明}）。 */
  public static BizException of(String errorCode, Object detail) {
    return of(errorCode, detail, null);
  }

  /**
   * 抛业务异常，附上下文说明与根因。
   *
   * <p><b>必须用四参构造</b>：{@code new BizException(status, message, cause)} 会把 {@code errorCode} 置
   * {@code null}，导致 {@code ProblemDetail} 拿不到码、前端 {@code i18n.t('errors.' + errorCode)}
   * 的分支永不命中（英文用户只能 看到中文 message）。这里把 {@code errorCode} 作为独立字段写入异常，与 {@link #DEFAULT_HTTP_STATUS}
   * 的状态配对一并 成为真源。
   *
   * <p>{@code message} 仍保留 {@code 码: 说明} 形态：它是 {@code errorCode} 缺失时的展示 fallback，也是日志/告警按码 聚合的兜底。
   */
  public static BizException of(String errorCode, Object detail, Throwable cause) {
    return new BizException(
        httpStatusOf(errorCode), composeMessage(errorCode, detail), errorCode, cause);
  }

  /** 供 {@code Optional.orElseThrow(...)} 使用的延迟构造器（正常分支不付构造开销）。 */
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
              + "；请在 StudioErrors 的 DEFAULT_HTTP_STATUS 中登记，"
              + "否则该异常会被兜底成 400/500。");
    }
    return status;
  }

  private static String composeMessage(String errorCode, Object detail) {
    return detail == null ? errorCode : errorCode + ": " + detail;
  }

  /**
   * 校验 {@link StudioErrorCodes} 的每个码常量都在状态表中。
   *
   * <p>用反射而非人工清单：人工清单本身就是又一份需要同步的数据，与「消除配对漂移」的初衷相悖。
   */
  private static void checkEveryCodeRegistered() {
    for (Field field : StudioErrorCodes.class.getDeclaredFields()) {
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
            "错误码常量未在 StudioErrors 登记默认 HTTP 状态: "
                + "StudioErrorCodes."
                + field.getName()
                + " = \""
                + code
                + "\"。新增错误码必须同步登记状态（错误码登记 §7）。");
      }
    }
  }
}
