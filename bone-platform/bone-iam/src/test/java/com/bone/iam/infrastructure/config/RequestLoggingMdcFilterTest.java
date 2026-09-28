package com.bone.iam.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * {@link RequestLoggingMdcFilter} 单测（M-2）：验证 MDC 注入与请求结束后清理， 防止线程复用导致 {@code tenantId}/{@code
 * traceId} 串号。
 */
class RequestLoggingMdcFilterTest {

  private final RequestLoggingMdcFilter filter = new RequestLoggingMdcFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void injectsTenantAndTraceIdFromHeaders() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getHeader("X-Tenant-Id")).thenReturn("42");
    when(request.getHeader("X-Trace-Id")).thenReturn("abc123");

    FilterChain chain =
        (req, res) -> {
          assertThat(MDC.get("tenantId")).isEqualTo("42");
          assertThat(MDC.get("traceId")).isEqualTo("abc123");
        };

    filter.doFilter(request, response, chain);
    // 请求结束后 MDC 必须清理，避免串号
    assertThat(MDC.get("tenantId")).isNull();
    assertThat(MDC.get("traceId")).isNull();
  }

  @Test
  void generatesTraceIdWhenHeaderMissing() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getHeader("X-Tenant-Id")).thenReturn(null);
    when(request.getHeader("X-Trace-Id")).thenReturn(null);

    FilterChain chain =
        (req, res) -> {
          assertThat(MDC.get("tenantId")).isNull();
          assertThat(MDC.get("traceId")).isNotNull();
        };

    filter.doFilter(request, response, chain);
    assertThat(MDC.get("traceId")).isNull();
  }
}
