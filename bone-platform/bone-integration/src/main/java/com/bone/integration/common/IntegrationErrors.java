package com.bone.integration.common;

import com.bone.core.exception.BizException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 业务错误码 → HTTP 状态 的<strong>唯一配对真源</strong>，以及抛出 {@link BizException} 的统一工厂。
 *
 * <p><b>为何需要它</b>：{@code BizException} 的第一个参数是 <em>HTTP 状态</em>，业务码是另一个字段。若在每个
 * 抛出点手写状态与码，配对就散落在全模块几十处，任一处不一致都没有机制发现（错误码登记 §17 禁止把 HTTP 码与业务码 混为同一个整数）。本类把配对收回一张表。
 *
 * <p><b>为何必须把 errorCode 显式传给 {@link BizException}</b>：{@code IntegrationExceptionAdvice} 用 {@code
 * ex.getErrorCode()} 填 {@code ProblemDetail.errorCode}，而三参构造会把 {@code errorCode} 置为 {@code
 * null}——那样响应体会退化成 {@code COMMON_VALIDATION_FAILED}，前端依旧拿不到语义码（这正是本模块 原 advice
 * 注释里自陈的缺陷）。故此处一律走四参构造。
 *
 * <p><b>新增错误码的强制约束</b>：{@link IntegrationErrorCodes} 的每个 public String 常量都必须在表中登记状态， 否则类加载即抛 {@link
 * IllegalStateException}——漏登记不可能溜到运行期（fail fast，而不是被兜底成 400）。
 */
public final class IntegrationErrors {

  /** 码 → 默认 HTTP 状态（真源）；{@link #checkEveryCodeRegistered()} 保证不会漏。 */
  private static final Map<String, Integer> DEFAULT_HTTP_STATUS =
      Map.ofEntries(
          Map.entry(IntegrationErrorCodes.CONNECTOR_NOT_FOUND, 404),
          Map.entry(IntegrationErrorCodes.CONNECTOR_NAME_CONFLICT, 409),
          Map.entry(IntegrationErrorCodes.CONNECTOR_TYPE_UNSUPPORTED, 400),
          Map.entry(IntegrationErrorCodes.CONNECTOR_NOT_IMPLEMENTED, 501),
          // 下游依赖故障用 502（Bad Gateway 语义）：本服务正常，是被调用方不可达/超时/报错
          Map.entry(IntegrationErrorCodes.CONNECTOR_INVOCATION_FAILED, 502),
          Map.entry(IntegrationErrorCodes.FLOW_NOT_FOUND, 404),
          Map.entry(IntegrationErrorCodes.FLOW_NAME_CONFLICT, 409),
          Map.entry(IntegrationErrorCodes.FLOW_NOT_ACTIVE, 409),
          Map.entry(IntegrationErrorCodes.FLOW_NODES_EMPTY, 400),
          Map.entry(IntegrationErrorCodes.FLOW_START_NODE_MISSING, 400),
          Map.entry(IntegrationErrorCodes.FLOW_END_NODE_MISSING, 400),
          Map.entry(IntegrationErrorCodes.EXECUTION_NOT_FOUND, 404));

  static {
    checkEveryCodeRegistered();
  }

  private IntegrationErrors() {}

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
    String message = detail == null ? errorCode : errorCode + ": " + detail;
    return new BizException(httpStatusOf(errorCode), message, errorCode, cause);
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
              + "；请在 IntegrationErrors 的 DEFAULT_HTTP_STATUS 中登记，"
              + "否则该异常会被 IntegrationExceptionAdvice 兜底成 400。");
    }
    return status;
  }

  /**
   * 校验 {@link IntegrationErrorCodes} 的每个码常量都在状态表中。
   *
   * <p>用反射而非人工清单：人工清单本身就是又一份需要同步的数据，与「消除配对漂移」的初衷相悖。
   */
  private static void checkEveryCodeRegistered() {
    for (Field field : IntegrationErrorCodes.class.getDeclaredFields()) {
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
            "错误码常量未在 IntegrationErrors 登记默认 HTTP 状态: "
                + "IntegrationErrorCodes."
                + field.getName()
                + " = \""
                + code
                + "\"。新增错误码必须同步登记状态（错误码登记 §7）。");
      }
    }
  }
}
