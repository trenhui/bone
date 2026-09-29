package com.bone.gateway.config;

/**
 * 网关传输层错误码（设计稿 §4.2，登记见 {@code doc/architecture/Bone-错误码登记.md} §6）。
 *
 * <p><b>裁决（设计稿 §1.1）</b>：网关<b>不产生业务错误码</b>，只产生传输层错误（401/429/503/404）。 但它是前端唯一入口，错误信封必须与平台 {@code
 * ApiResponse} 一致，且带稳定码供前端 i18n—— 否则前端无法对 401/429/503 做统一解析与提示。
 *
 * <p><b>用法</b>：随 {@code GatewayErrorWriter.write(exchange, status, errorCode, message)} 写入响应体
 * {@code errorCode} 字段；{@code traceId} 不进 body，走响应头 {@code X-Trace-Id}（由 TraceIdRelayGatewayFilter
 * 写入）。
 */
public final class GatewayErrorCodes {

  /** 无效或缺失 token（401）。 */
  public static final String UNAUTHORIZED = "GW_UNAUTHORIZED";

  /** 触发限流（429）。 */
  public static final String RATE_LIMITED = "GW_RATE_LIMITED";

  /** 下游不可用 / 熔断打开（503）。 */
  public static final String UPSTREAM_UNAVAILABLE = "GW_UPSTREAM_UNAVAILABLE";

  /** 无匹配路由（404）。当前 404 由 Spring Cloud Gateway 默认产生，尚未走统一信封；登记此码供后续 404 兜底过滤器使用。 */
  public static final String ROUTE_NOT_FOUND = "GW_ROUTE_NOT_FOUND";

  private GatewayErrorCodes() {}
}
