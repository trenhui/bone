package com.bone.blueprint.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 请求上下文与 Access Log（《Bone-日志规范》§3 / §5 的落地实现）。
 *
 * <p><b>为什么必须有这一层</b>：规范把 MDC 列为<strong>强制</strong>——没有 {@code traceId}，出错时无法把「用户报的那一次请求」
 * 与日志、{@code ProblemDetail.traceId}、下游 MQ 消息串起来；没有 {@code tenantId}/{@code userId}，多租户排障只能靠猜。
 *
 * <p><b>类名为什么带 {@code Bone} 前缀（不是风格洁癖）</b>：Spring Boot 的 {@code WebMvcAutoConfiguration} 已经注册了一个 名为
 * {@code requestContextFilter} 的 Bean（框架自带的 {@code
 * org.springframework.web.filter.RequestContextFilter}）。 若本类简单名取 {@code
 * RequestContextFilter}，{@code @Component} 的默认 Bean 名会与之同名，启动即抛 {@code
 * BeanDefinitionOverrideException}——<strong>应用完全起不来</strong>，而单元测试不加载容器，照样全绿。带前缀既避开
 * 冲突，也与《Bone-日志规范》里给出的示例实现名（{@code BoneRequestContextFilter}）一致。
 *
 * <p><b>为什么用 {@link Ordered#HIGHEST_PRECEDENCE}</b>：必须早于 Spring Security 过滤链，才能让安全链内部的日志 （含认证失败
 * WARN）也带上 {@code traceId}；同时它是最外层，{@code finally} 里清理 MDC 才不会污染线程池中的后续请求。
 *
 * <p><b>为什么 {@code tenantId} / {@code userId} 由认证过滤器写入 MDC，而不是在这里读</b>：Spring Security 的 {@code
 * SecurityContextHolderFilter} 会在安全链结束时就清空上下文，而本过滤器的 {@code finally} 在安全链<strong>之后</strong>才执行——
 * 那时再读 {@code SecurityContext} 只会读到空值。租户上下文同理（{@code TenantInterceptor.afterCompletion} 已清理）。
 * 因此身份类字段在链内写入 MDC，本类只负责读取与清理（见 {@code JwtAuthenticationFilter#onAuthenticated}）。
 *
 * <p>模块级实现，建议后续下沉到 {@code bone-web} 供全部模块复用（当前平台尚无统一实现）。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BoneRequestContextFilter extends OncePerRequestFilter {

  /** MDC 键名，与《Bone-日志规范》§3 表格一致（下同）。 */
  public static final String MDC_TRACE_ID = "traceId";

  public static final String MDC_TENANT_ID = "tenantId";
  public static final String MDC_USER_ID = "userId";
  public static final String MDC_DOMAIN = "domain";
  public static final String MDC_HTTP_METHOD = "httpMethod";
  public static final String MDC_HTTP_ROUTE = "httpRoute";

  /** 请求 ID 头：客户端/网关可传入，无则本服务生成（日志规范 §3 / API 规范 §6.1）。 */
  public static final String REQUEST_ID_HEADER = "X-Request-Id";

  /** 慢请求阈值（毫秒）：超过则额外打一条 WARN（日志规范 §5）。 */
  private static final long SLOW_REQUEST_MS = 3000L;

  private final String domain;

  public BoneRequestContextFilter(
      @Value("${spring.application.name:blueprint}") String applicationName) {
    // domain 用于日志分组：bone-blueprint → blueprint
    this.domain =
        applicationName.startsWith("bone-") ? applicationName.substring(5) : applicationName;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String traceId = resolveTraceId(request);
    MDC.put(MDC_TRACE_ID, traceId);
    MDC.put(MDC_DOMAIN, domain);
    MDC.put(MDC_HTTP_METHOD, request.getMethod());
    response.setHeader(REQUEST_ID_HEADER, traceId);

    long start = System.currentTimeMillis();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long costMs = System.currentTimeMillis() - start;
      // httpRoute 取 URI 模板（/api/v1/orders/{id}），避免把原始 ID 路径写进日志与指标标签
      String route = resolveRoute(request);
      MDC.put(MDC_HTTP_ROUTE, route);
      log.info(
          "[API] method={} route={} status={} durationMs={} traceId={} tenantId={} userId={}",
          request.getMethod(),
          route,
          response.getStatus(),
          costMs,
          traceId,
          MDC.get(MDC_TENANT_ID),
          MDC.get(MDC_USER_ID));
      if (costMs > SLOW_REQUEST_MS) {
        log.warn("[API] 慢请求 method={} route={} durationMs={}", request.getMethod(), route, costMs);
      }
      // 必须清理：线程池复用线程时残留的 MDC 会串到下一个请求上
      MDC.clear();
    }
  }

  /** 无 {@code X-Request-Id} 时生成一个（去掉连字符，与日志规范示例一致）。 */
  private String resolveTraceId(HttpServletRequest request) {
    return Optional.ofNullable(request.getHeader(REQUEST_ID_HEADER))
        .filter(id -> !id.isBlank())
        .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
  }

  /** 优先用 Spring MVC 已解析的 URI 模板；未进入 MVC（如 404、静态资源）时退回原始 URI。 */
  private String resolveRoute(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return pattern != null ? pattern.toString() : request.getRequestURI();
  }
}
