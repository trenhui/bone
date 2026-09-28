package com.bone.system.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * S-9（可观测性）：请求级日志 MDC 注入。
 *
 * <p>每个请求在 {@code MDC} 中写入 {@code tenantId}（取自营门口真源 {@code X-Tenant-Id} 头， 与 {@code
 * TenantInterceptor} 同源）与 {@code traceId}（优先取 {@code X-Trace-Id} 头，缺省生成 8 位随机串）， 使 bone-system 的
 * {@code logback-spring.xml} pattern 能在每条日志前带上租户与链路标识，便于跨服务排查。
 *
 * <p>以 {@link Order#LOWEST_PRECEDENCE - 100} 注册，确保包裹整个请求（在 TenantInterceptor / 安全过滤器之后仍可读到租户头）。
 * 请求结束后 {@code finally} 中清除，避免线程复用导致 MDC 串号。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class RequestLoggingMdcFilter extends OncePerRequestFilter {

  private static final String TENANT_ID_HEADER = "X-Tenant-Id";
  private static final String TRACE_ID_HEADER = "X-Trace-Id";

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String tenantId = request.getHeader(TENANT_ID_HEADER);
      if (tenantId != null && !tenantId.isBlank()) {
        MDC.put("tenantId", tenantId);
      }
      String traceId = request.getHeader(TRACE_ID_HEADER);
      if (traceId == null || traceId.isBlank()) {
        traceId = UUID.randomUUID().toString().substring(0, 8);
      }
      MDC.put("traceId", traceId);
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove("tenantId");
      MDC.remove("traceId");
    }
  }
}
