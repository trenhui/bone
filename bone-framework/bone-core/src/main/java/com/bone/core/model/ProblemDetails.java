package com.bone.core.model;

import org.slf4j.MDC;

/**
 * {@link ProblemDetail} 的**统一构造入口**（i18n 方案 §6.2.1「把 problem 工厂上提到 bone-core」）。
 *
 * <p><b>为何需要它</b>：此前 metadata-server 有 {@code CatalogApiResponses}、extension-studio 有 {@code
 * StudioCommandResponses}，两份几乎相同却不等价的 {@code problem(...)} 实现；IAM 又自建 {@code
 * LinkedHashMap}。三份并行会固化出「多种 ProblemDetail 形状」，前端类型、契约测试、OpenAPI 校验都要分叉。 本类收口为唯一实现，各模块不再各自复制。
 *
 * <p><b>统一了什么</b>：
 *
 * <ul>
 *   <li>{@code errorCode} 必填（可传 null，仅用于未改造的旧路径），是前端 {@code i18n.t('errors.' + errorCode)} 的键；
 *   <li>{@code traceId} 统一从 MDC 取，不再各模块自行约定 key；
 *   <li>{@code instance} 统一为请求 URI；
 *   <li>{@code title} 用 HTTP 状态文本（便于人读），{@code type} 用 about:blank + errorCode 形态的 URI。
 * </ul>
 *
 * <p><b>不承载文案</b>：{@code detail} 保持中文（fallback 契约），展示文案由前端语言包负责。
 */
public final class ProblemDetails {

  /** MDC 中 traceId 的 key（与全链路日志保持一致）。 */
  public static final String TRACE_ID_KEY = "traceId";

  private ProblemDetails() {}

  /**
   * 构造 ProblemDetail。
   *
   * @param errorCode 稳定业务码（如 {@code BP_ORDER_NOT_FOUND}）；未改造路径可传 null
   * @param httpStatus HTTP 状态码
   * @param detail 中文明细（fallback 文案）
   * @param instance 请求 URI，可为 null
   */
  public static ProblemDetail of(String errorCode, int httpStatus, String detail, String instance) {
    ProblemDetail problem = ProblemDetail.of(errorCode, httpStatus, detail);
    problem.setInstance(instance);
    problem.setType(typeOf(errorCode));
    problem.setTitle(httpStatusText(httpStatus));
    problem.setTraceId(MDC.get(TRACE_ID_KEY));
    return problem;
  }

  /** 无请求上下文（如 Filter / 定时任务侧）的简化构造。 */
  public static ProblemDetail of(String errorCode, int httpStatus, String detail) {
    return of(errorCode, httpStatus, detail, null);
  }

  private static String typeOf(String errorCode) {
    return errorCode == null || errorCode.isBlank()
        ? "about:blank"
        : "https://bone.wps.cn/problems/" + errorCode.toLowerCase().replace('_', '-');
  }

  private static String httpStatusText(int status) {
    return switch (status) {
      case 400 -> "Bad Request";
      case 401 -> "Unauthorized";
      case 403 -> "Forbidden";
      case 404 -> "Not Found";
      case 405 -> "Method Not Allowed";
      case 409 -> "Conflict";
      case 423 -> "Locked";
      case 429 -> "Too Many Requests";
      case 500 -> "Internal Server Error";
      case 502 -> "Bad Gateway";
      case 503 -> "Service Unavailable";
      default -> "Error";
    };
  }
}
