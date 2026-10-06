package com.bone.studio.generator.common;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.exception.BizException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 「业务错误码 → HTTP 状态」的<strong>唯一配对真源</strong>，以及抛出 {@link BizException} 的统一工厂。
 *
 * <p><b>为何需要它</b>：{@link BizException} 的第一个参数是 <em>HTTP 状态</em>，业务码走独立的 {@code errorCode}
 * 字段，两者语义不同。 本模块此前把码当字符串硬编码在 Map 里（{@code error.put("errorCode",
 * "GEN_GENERATION_FAILED")}），更糟的一处甚至把码拼进 message（{@code "GEN_TEMPLATE_NOT_FOUND: " +
 * id}）——前端只能靠解析中文文案猜错误类型，{@code i18n.t('errors.' + errorCode)} 的分支永不命中。
 *
 * <p><b>新增错误码的强制约束</b>：{@link GeneratorErrorCodes} 的每个 {@code String} 常量都必须在表中登记状态，否则类加载即抛 {@link
 * IllegalStateException}（fail fast），不会等到线上才被兜底成 400/500。
 */
public final class GeneratorErrors {

  /** 码 → 默认 HTTP 状态（真源）；{@link #checkEveryCodeRegistered()} 保证不会漏。 */
  private static final Map<String, Integer> DEFAULT_HTTP_STATUS =
      Map.ofEntries(
          Map.entry(GeneratorErrorCodes.TEMPLATE_NOT_FOUND, 404),
          Map.entry(GeneratorErrorCodes.GENERATION_FAILED, 500),
          Map.entry(GeneratorErrorCodes.TENANT_CONTEXT_MISSING, 400),
          Map.entry(CommonErrorCodes.VALIDATION_FAILED, 400),
          Map.entry(CommonErrorCodes.FORBIDDEN, 403),
          Map.entry(CommonErrorCodes.INTERNAL_ERROR, 500),
          Map.entry(CommonErrorCodes.NOT_FOUND, 404));

  static {
    checkEveryCodeRegistered();
  }

  private GeneratorErrors() {}

  /** 抛业务异常，附业务标识或上下文说明（拼成 {@code 码: 说明}）。 */
  public static BizException of(String errorCode, Object detail) {
    return of(errorCode, detail, null);
  }

  /**
   * 抛业务异常，附上下文说明与根因。
   *
   * <p><b>必须用四参构造</b>：{@code new BizException(status, message, cause)} 会把 {@code errorCode} 置
   * {@code null}， 导致响应体拿不到码。这里把 {@code errorCode} 作为独立字段写入异常，与 {@link #DEFAULT_HTTP_STATUS}
   * 的状态配对一并成为真源。
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
              + "；请在 GeneratorErrors 的 DEFAULT_HTTP_STATUS 中登记，"
              + "否则该异常会被兜底成 400/500。");
    }
    return status;
  }

  private static String composeMessage(String errorCode, Object detail) {
    return detail == null ? errorCode : errorCode + ": " + detail;
  }

  /**
   * 校验 {@link GeneratorErrorCodes} 的每个码常量都在状态表中。
   *
   * <p>用反射而非人工清单：人工清单本身就是又一份需要同步的数据，与「消除配对漂移」的初衷相悖。
   */
  private static void checkEveryCodeRegistered() {
    for (Field field : GeneratorErrorCodes.class.getDeclaredFields()) {
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
            "错误码常量未在 GeneratorErrors 登记默认 HTTP 状态: "
                + "GeneratorErrorCodes."
                + field.getName()
                + " = \""
                + code
                + "\"。新增错误码必须同步登记状态。");
      }
    }
  }
}
