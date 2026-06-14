package com.bone.engine.extension.studio.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** MDC + Access Log（Bone-API-规范 §10.2）。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StudioRequestContextFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(StudioRequestContextFilter.class);

  public static final String TRACE_ID = "traceId";
  public static final String DOMAIN = "extension";
  private static final String HEADER_TRACE = "X-Trace-Id";
  private static final String HEADER_REQUEST_ID = "X-Request-Id";
  private static final long SLOW_MS = 3000L;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String traceId = resolveTraceId(request);
    MDC.put(TRACE_ID, traceId);
    MDC.put("domain", DOMAIN);
    MDC.put("httpMethod", request.getMethod());
    MDC.put("httpPath", request.getRequestURI());
    response.setHeader(HEADER_TRACE, traceId);
    response.setHeader(HEADER_REQUEST_ID, traceId);

    long start = System.currentTimeMillis();
    try {
      chain.doFilter(request, response);
    } finally {
      long durationMs = System.currentTimeMillis() - start;
      int status = response.getStatus();
      if (status >= 400) {
        log.warn(
            "[API] traceId={} method={} path={} status={} durationMs={} tenantId={} userId={}",
            traceId,
            request.getMethod(),
            request.getRequestURI(),
            status,
            durationMs,
            MDC.get("tenantId"),
            MDC.get("userId"));
      } else if (durationMs > SLOW_MS) {
        log.warn(
            "[API] slow traceId={} method={} path={} status={} durationMs={}",
            traceId,
            request.getMethod(),
            request.getRequestURI(),
            status,
            durationMs);
      } else if (shouldAccessLog(request)) {
        log.info(
            "[API] traceId={} method={} path={} status={} durationMs={} tenantId={} userId={}",
            traceId,
            request.getMethod(),
            request.getRequestURI(),
            status,
            durationMs,
            MDC.get("tenantId"),
            MDC.get("userId"));
      }
      MDC.clear();
    }
  }

  private static String resolveTraceId(HttpServletRequest request) {
    String fromHeader = request.getHeader(HEADER_TRACE);
    if (!StringUtils.hasText(fromHeader)) {
      fromHeader = request.getHeader(HEADER_REQUEST_ID);
    }
    if (StringUtils.hasText(fromHeader)) {
      return fromHeader.trim();
    }
    return UUID.randomUUID().toString().replace("-", "");
  }

  private static boolean shouldAccessLog(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return !uri.startsWith("/actuator")
        && !uri.startsWith("/swagger-ui")
        && !uri.startsWith("/v3/api-docs")
        && !uri.startsWith("/api-docs")
        && !uri.startsWith("/h2-console");
  }
}
