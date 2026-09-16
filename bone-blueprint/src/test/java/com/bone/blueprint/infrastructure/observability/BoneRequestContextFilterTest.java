package com.bone.blueprint.infrastructure.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 请求上下文过滤器测试（《Bone-日志规范》§3/§5/§9）。
 *
 * <p>锁住三件事：① 链内日志能拿到 {@code traceId}（否则安全链内的认证失败日志无法与请求关联）；② 响应回显 {@code X-Request-Id}（否则前端报错时拿不到排障
 * ID）；③ 请求结束必须清理 MDC（否则线程池复用会把上一个请求的租户 串到下一个请求）。
 */
class BoneRequestContextFilterTest {

  private final BoneRequestContextFilter filter = new BoneRequestContextFilter("bone-blueprint");

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void reusesIncomingRequestIdAndEchoesIt() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
    request.addHeader(BoneRequestContextFilter.REQUEST_ID_HEADER, "trace-from-client");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> traceInsideChain = new AtomicReference<>();
    AtomicReference<String> domainInsideChain = new AtomicReference<>();

    filter.doFilter(
        request,
        response,
        (req, res) -> {
          traceInsideChain.set(MDC.get(BoneRequestContextFilter.MDC_TRACE_ID));
          domainInsideChain.set(MDC.get(BoneRequestContextFilter.MDC_DOMAIN));
        });

    assertEquals("trace-from-client", traceInsideChain.get());
    assertEquals("blueprint", domainInsideChain.get());
    assertEquals(
        "trace-from-client", response.getHeader(BoneRequestContextFilter.REQUEST_ID_HEADER));
    assertNull(MDC.get(BoneRequestContextFilter.MDC_TRACE_ID), "必须在 finally 清理 MDC，避免线程池串号");
  }

  @Test
  void generatesRequestIdWhenAbsent() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> traceInsideChain = new AtomicReference<>();

    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/v1/orders"),
        response,
        (req, res) -> traceInsideChain.set(MDC.get(BoneRequestContextFilter.MDC_TRACE_ID)));

    assertFalse(traceInsideChain.get().isBlank());
    assertEquals(
        traceInsideChain.get(), response.getHeader(BoneRequestContextFilter.REQUEST_ID_HEADER));
  }

  @Test
  void usesUriTemplateWhenAvailable() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/v1/orders/900000000000000001");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/orders/{id}");

    // 访问日志用 URI 模板而非含 ID 的原始路径（高基数标签会打爆指标，日志规范 §3）
    assertEquals(
        "/api/v1/orders/{id}",
        String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)));
  }
}
