package com.bone.metadata.catalog.common;

import com.bone.core.exception.BizException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 业务错误码 → HTTP 状态的<strong>唯一配对真源</strong>，以及抛出 {@link BizException} 的统一工厂。
 *
 * <p><b>为何需要它</b>：本模块此前<b>只有 {@link CatalogErrorCodes} 而没有本类</b>， 于是全部抛出点退化为 {@code
 * BizException.of("实体不存在: " + id)} —— 而 {@code BizException} 的 9 个静态 {@code of()} 重载<b>无一携带
 * errorCode</b>（它们把 {@code errorCode} 硬置 null），于是 {@code ProblemDetail.errorCode} 恒为空 ⇒ 前端拿不到
 * {@code i18n.t('errors.' + errorCode)} 的键、监控/告警只能按中文文案聚合， 文案一改聚合口径即断。且状态被兜底成 <b>500</b>，把「查不到 / 冲突
 * / 参数非法」全报成服务端故障， 污染 5xx 告警与 SLO。
 *
 * <p><b>本类的做法</b>：把配对收进 {@link #DEFAULT_HTTP_STATUS} 一张表， 抛出点只表达<strong>业务语义</strong>（哪个码 + 什么上下文）。
 *
 * <p><b>新增错误码的强制约束</b>：{@link CatalogErrorCodes} 的每个 public String 常量都必须在表中登记状态， 否则类加载即抛 {@link
 * IllegalStateException} —— 漏登记不可能溜到运行期（fail fast）。
 *
 * <p>同构参照：{@code com.bone.blueprint.common.BlueprintErrors}。
 */
public final class CatalogErrors {

  /**
   * 码 → 默认 HTTP 状态（真源）。
   *
   * <p>404 = 资源不存在；400 = 入参非法；409 = 唯一性/状态冲突；502 = 上游物理结构不可用。
   */
  private static final Map<String, Integer> DEFAULT_HTTP_STATUS =
      Map.ofEntries(
          // 实体
          Map.entry(CatalogErrorCodes.ENTITY_NOT_FOUND, 404),
          Map.entry(CatalogErrorCodes.ENTITY_CODE_CONFLICT, 409),
          Map.entry(CatalogErrorCodes.ENTITY_TABLE_NAME_CONFLICT, 409),
          Map.entry(CatalogErrorCodes.ENTITY_COPY_CODE_REQUIRED, 400),
          Map.entry(CatalogErrorCodes.ENTITY_COPY_TABLE_NAME_REQUIRED, 400),
          Map.entry(CatalogErrorCodes.ENTITY_NOT_RUNTIME, 409),
          // 字段
          Map.entry(CatalogErrorCodes.FIELD_NOT_FOUND, 404),
          Map.entry(CatalogErrorCodes.FIELD_CODE_CONFLICT, 409),
          // 关系
          Map.entry(CatalogErrorCodes.RELATION_NOT_FOUND, 404),
          // 模板
          Map.entry(CatalogErrorCodes.TEMPLATE_NOT_FOUND, 404),
          Map.entry(CatalogErrorCodes.TEMPLATE_CODE_CONFLICT, 409),
          Map.entry(CatalogErrorCodes.TEMPLATE_NOT_PUBLISHED, 409),
          Map.entry(CatalogErrorCodes.TEMPLATE_ID_REQUIRED, 400),
          // 导入
          Map.entry(CatalogErrorCodes.PHYSICAL_TABLE_NOT_FOUND, 404),
          // 发布期漂移（保持收口前的 409 不变）
          Map.entry(CatalogErrorCodes.META_DOMAIN_ERROR, 409));

  static {
    checkEveryCodeRegistered();
  }

  private CatalogErrors() {}

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
   * <p><b>i18n 关键：码随异常走</b> —— {@code errorCode} 直接写进 {@link BizException#getErrorCode()}，由
   * bone-web的 {@code GlobalExceptionHandler} 透传进 {@code ProblemDetail.errorCode}。因此 handler
   * <strong>不需要</strong>从 message 里解析码。
   */
  public static BizException of(String errorCode, Object detail, Throwable cause) {
    return new BizException(
        httpStatusOf(errorCode), composeMessage(errorCode, detail), errorCode, cause);
  }

  /**
   * 供 {@code Optional.orElseThrow(...)} / {@code Optional.map(...)} 使用的延迟构造器。
   *
   * <p>用 Supplier 而非直接构造异常，是为了让「聚合不存在」这类正常分支<b>不付出构造开销</b>（含堆栈填充）。
   */
  public static Supplier<BizException> supplier(String errorCode, Object detail) {
    return () -> of(errorCode, detail);
  }

  /** 取错误码的默认 HTTP 状态；未登记即抛 —— 这是本类的核心不变量，不做兜底。 */
  public static int httpStatusOf(String errorCode) {
    Integer status = DEFAULT_HTTP_STATUS.get(errorCode);
    if (status == null) {
      throw new IllegalStateException(
          "错误码未登记默认 HTTP 状态: "
              + errorCode
              + "；请在 CatalogErrors 的 DEFAULT_HTTP_STATUS 中登记，"
              + "否则该异常会被 GlobalExceptionHandler 兜底成 400。");
    }
    return status;
  }

  private static String composeMessage(String errorCode, Object detail) {
    return detail == null ? errorCode : errorCode + ": " + detail;
  }

  /**
   * 校验 {@link CatalogErrorCodes} 的每个码常量都在状态表中。
   *
   * <p>用反射而非人工清单：人工清单本身就是又一份需要同步的数据，与「消除配对漂移」的初衷相悖。
   */
  private static void checkEveryCodeRegistered() {
    for (Field field : CatalogErrorCodes.class.getDeclaredFields()) {
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
            "错误码常量未在 CatalogErrors 登记默认 HTTP 状态: "
                + "CatalogErrorCodes."
                + field.getName()
                + " = \""
                + code
                + "\"。新增错误码必须同步登记状态。");
      }
    }
  }
}
