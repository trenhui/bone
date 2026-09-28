package com.bone.system.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RequestLoggingMdcFilterTest {

  private final RequestLoggingMdcFilter filter = new RequestLoggingMdcFilter();

  @Mock private FilterChain chain;

  /** 在链路执行期内断言 MDC 已写入，请求结束后必须清除。 */
  private void doFilterAndAssertInChain(
      MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
    filter.doFilter(request, response, chain);
    // 请求结束后必须清除，避免线程复用串号
    assertThat(MDC.get("tenantId")).isNull();
    assertThat(MDC.get("traceId")).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void writesMdcFromHeadersAndCleansUp() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Tenant-Id", "123");
    request.addHeader("X-Trace-Id", "abc-trace");
    MockHttpServletResponse response = new MockHttpServletResponse();

    doAnswer(
            inv -> {
              assertThat(MDC.get("tenantId")).isEqualTo("123");
              assertThat(MDC.get("traceId")).isEqualTo("abc-trace");
              return null;
            })
        .when(chain)
        .doFilter(any(), any());

    doFilterAndAssertInChain(request, response);
  }

  @Test
  void generatesTraceIdWhenHeaderMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Tenant-Id", "7");
    MockHttpServletResponse response = new MockHttpServletResponse();

    doAnswer(
            inv -> {
              assertThat(MDC.get("tenantId")).isEqualTo("7");
              assertThat(MDC.get("traceId")).isNotNull();
              assertThat(MDC.get("traceId")).hasSize(8);
              return null;
            })
        .when(chain)
        .doFilter(any(), any());

    doFilterAndAssertInChain(request, response);
  }

  @Test
  void skipsTenantIdWhenHeaderAbsent() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    doAnswer(
            inv -> {
              assertThat(MDC.get("tenantId")).isNull();
              return null;
            })
        .when(chain)
        .doFilter(any(), any());

    doFilterAndAssertInChain(request, response);
    assertThat(MDC.get("traceId")).isNull();
  }
}
