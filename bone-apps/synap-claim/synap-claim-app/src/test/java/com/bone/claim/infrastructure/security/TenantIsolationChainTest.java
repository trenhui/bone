package com.bone.claim.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.bone.claim.infrastructure.tenant.TenantPortAdapter;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtTokenService;
import com.bone.core.tenant.context.TenantContext;
import com.bone.core.web.interceptor.TenantInterceptor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 租户隔离 CI 自动化护栏 · 链路级（诊断 P1-5：把「过滤器归一化 → 拦截器播种上下文 → 端口读取」的<b>真实接线</b>锁死）。
 *
 * <p>与 {@link TenantIsolationGuardrailTest}（单类不变量）互补：此处不启动 Spring 上下文，而是直接编排生产中 Spring 实际装配的
 * 两个真实组件——{@link JwtAuthenticationFilter}（归一化 {@code X-Tenant-Id} 为签名 claim）与 {@link
 * TenantInterceptor}（从归一化后的头播种 {@link TenantContext}）——断言<b>伪造头永远无法抵达数据层</b>：
 *
 * <ol>
 *   <li>持租户 A 的合法 token + 伪造 {@code X-Tenant-Id: B} → 链路结束后 {@link TenantContext} 与 {@link
 *       TenantPortAdapter#currentTenantId()} 解析出的租户只能是 A，绝不可能是 B；
 *   <li>无伪造头时，链路仍只解析出 token 内签名租户；
 *   <li>入站完全缺 {@code X-Tenant-Id}（且 token 亦无 claim 的退化场景） → 上下文保持空 → 端口 fail-closed 抛 {@link
 *       IllegalStateException}，不回落默认值。
 * </ol>
 *
 * <p>毫秒级、无 DB/无 Spring 上下文，纳入 CI 默认门禁；一旦有人改掉 {@code SecurityConfig} 的过滤器接线、或 {@link
 * WebMvcConfiguration} 漏注 {@link TenantInterceptor}，本测试立即转红。注意类名不能以 {@code Abstract} 开头——Surefire 按
 * {@code Abstract*.java} 排除，会静默不执行。
 */
class TenantIsolationChainTest {

  private static final String TENANT_HEADER = "X-Tenant-Id";
  private static final long CLAIM_TENANT = 1001L;
  private static final long FORGED_TENANT = 9999L;

  private JwtAuthenticationFilter filter;
  private TenantInterceptor interceptor;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
    JwtConfig jwtConfig = new JwtConfig(mock(ConfigurableEnvironment.class));
    jwtConfig.setSecretKey("test-secret-key-32-bytes-minimum-length!!");
    JwtTokenService jwtTokenService = new JwtTokenService(jwtConfig);
    filter = new JwtAuthenticationFilter(jwtTokenService, jwtConfig);
    interceptor = new TenantInterceptor();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
  }

  @Test
  void forgedHeaderNeverReachesDataLayer_chainResolvesToSignedTenant() {
    MockHttpServletRequest request = tokenRequest(CLAIM_TENANT, String.valueOf(FORGED_TENANT));

    runChain(request);

    // 数据层唯一权威（TenantPortAdapter）读到的必须是签名租户 A，绝不可能是伪造的 B
    assertThat(TenantContext.getTenantId()).isEqualTo(String.valueOf(CLAIM_TENANT));
    assertThat(new TenantPortAdapter().currentTenantId()).isEqualTo(CLAIM_TENANT);
    assertThat(TenantContext.getTenantId()).isNotEqualTo(String.valueOf(FORGED_TENANT));
  }

  @Test
  void normalRequestWithoutForgedHeader_chainResolvesToTokenTenant() {
    MockHttpServletRequest request = tokenRequest(CLAIM_TENANT, null);

    runChain(request);

    assertThat(TenantContext.getTenantId()).isEqualTo(String.valueOf(CLAIM_TENANT));
    assertThat(new TenantPortAdapter().currentTenantId()).isEqualTo(CLAIM_TENANT);
  }

  @Test
  void missingHeaderChainFailsClosed_notDefaultTenant() {
    // 退化场景：入站完全无 X-Tenant-Id（且 token 无有效 claim 的极端畸形入站）
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/claims");
    // 不放 Authorization、不放 X-Tenant-Id

    // 拦截器 fail-closed：上下文保持空，不回落租户 0
    interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    try {
      assertThat(TenantContext.getTenantId()).isNull();
      // 端口读缺失上下文 → 抛异常（与 TenantIsolationGuardrailTest 同口径，端到端复验）
      assertThatThrownBy(() -> new TenantPortAdapter().currentTenantId())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("租户上下文缺失");
    } finally {
      interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
    }
  }

  // ==== 辅助 ====

  private MockHttpServletRequest tokenRequest(long claimTenant, String forgedHeader) {
    JwtConfig jwtConfig = new JwtConfig(mock(ConfigurableEnvironment.class));
    jwtConfig.setSecretKey("test-secret-key-32-bytes-minimum-length!!");
    String token =
        new JwtTokenService(jwtConfig).generateToken(1L, "alice", claimTenant, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/claims");
    request.addHeader("Authorization", "Bearer " + token);
    if (forgedHeader != null) {
      request.addHeader(TENANT_HEADER, forgedHeader);
    }
    return request;
  }

  /** 复刻生产装配顺序：先过认证过滤器（归一化头、并以包装请求透传下游），再过拦截器（播种上下文）。 */
  private void runChain(MockHttpServletRequest request) {
    // 关键点：过滤器对带 token 请求会把 X-Tenant-Id 归一化为签名 claim，并以包装后的请求对象透传给下游
    // （AbstractJwtAuthenticationFilter.TenantHeaderOverridingRequest）。生产中 Spring 的拦截器看到的正是这个包装请求，
    // 因此必须把过滤器实际交给下游的请求喂给拦截器——否则测的是原始请求、与生产行为不符。
    AtomicReference<HttpServletRequest> downstream = new AtomicReference<>();
    FilterChain chain = (req, res) -> downstream.set((HttpServletRequest) req);
    try {
      filter.doFilter(request, new MockHttpServletResponse(), chain);
      HttpServletRequest seenByInterceptor = downstream.get() != null ? downstream.get() : request;
      interceptor.preHandle(seenByInterceptor, new MockHttpServletResponse(), new Object());
    } catch (Exception e) {
      throw new IllegalStateException("租户隔离链路执行失败", e);
    }
  }
}
