package com.bone.claim.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.bone.claim.infrastructure.tenant.TenantPortAdapter;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtTokenService;
import com.bone.core.tenant.context.TenantContext;
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
 * 租户隔离 CI 自动化护栏（诊断 P1-5：设计强但「靠人守」 → 落为自动化反例）。
 *
 * <p><b>锁住两条 OWASP A01 fail-closed 不变量</b>：
 *
 * <ol>
 *   <li>认证过滤器层面：持租户 A 的合法 token 时，伪造的 {@code X-Tenant-Id: B} 请求头必须被改写为 token 内 <b>已签名</b>的
 *       claim（A），下游（TenantInterceptor / 数据源路由）读到的只能是 A，绝不可能是 B。
 *   <li>租户权威层面：模块唯一租户权威 {@link TenantPortAdapter} 只认 {@link TenantContext} （由认证过滤器从签名 claim
 *       播种），不读原始请求头；上下文缺失时 fail-closed 抛异常，绝不回落默认值。
 * </ol>
 *
 * <p>本测试刻意不启动 Spring 上下文（纯过滤器/适配器单元），毫秒级、可纳入 CI 默认门禁。平台层 {@code
 * JwtAuthenticationFilterTenantHeaderTest} 已覆盖抽象过滤器自身的归一化，此处下沉到 <b>模块具体过滤器</b> 与 <b>模块租户端口</b>，证明
 * synap-claim 的接线同样守住了边界。
 *
 * <p>注意类名不能以 {@code Abstract} 开头——Surefire 按 {@code Abstract*.java} 排除，会静默不执行。
 */
class TenantIsolationGuardrailTest {

  private static final String SECRET = "test-secret-key-32-bytes-minimum-length!!";
  private static final String TENANT_HEADER = "X-Tenant-Id";
  private static final long CLAIM_TENANT = 1001L;
  private static final long FORGED_TENANT = 9999L;

  private JwtTokenService jwtTokenService;
  private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    // 过滤器对「已认证」请求会跳过一次处理；单测需自建线程本地上下文并清理，否则用例间串味。
    SecurityContextHolder.clearContext();
    JwtConfig jwtConfig = new JwtConfig(mock(ConfigurableEnvironment.class));
    jwtConfig.setSecretKey(SECRET);
    jwtTokenService = new JwtTokenService(jwtConfig);
    filter = new JwtAuthenticationFilter(jwtTokenService, jwtConfig);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
  }

  // === 不变量 1：认证过滤器层伪造头被签名 claim 覆盖 ===

  @Test
  void forgedTenantHeaderIsOverriddenBySignedClaim() {
    HttpServletRequest seen = runFilter(tokenRequest(CLAIM_TENANT, String.valueOf(FORGED_TENANT)));

    assertThat(seen.getHeader(TENANT_HEADER)).isEqualTo(String.valueOf(CLAIM_TENANT));
  }

  @Test
  void platformTenantClaimOverridesForgedHeader() {
    // 平台租户 0 是最常见的 claim，同样不得放行伪造头
    HttpServletRequest seen = runFilter(tokenRequest(0L, String.valueOf(FORGED_TENANT)));

    assertThat(seen.getHeader(TENANT_HEADER)).isEqualTo("0");
  }

  @Test
  void tenantTokenWithActingHeaderIsRejected() {
    // 租户 token 即便伪造平台切换头，也强制归一化为自身 claim（ADR-0029 fail-closed 不被削弱）
    MockHttpServletRequest request = tokenRequest(CLAIM_TENANT, String.valueOf(CLAIM_TENANT));
    request.addHeader("X-Acting-Tenant-Id", String.valueOf(FORGED_TENANT));

    assertThat(runFilter(request).getHeader(TENANT_HEADER)).isEqualTo(String.valueOf(CLAIM_TENANT));
  }

  // === 不变量 2：租户权威只认 TenantContext、缺失即 fail-closed ===

  @Test
  void tenantPortAdapterReadsOnlyTenantContext() {
    TenantContext.setTenantId(CLAIM_TENANT);

    assertThat(new TenantPortAdapter().currentTenantId()).isEqualTo(CLAIM_TENANT);
  }

  @Test
  void tenantPortAdapterFailsClosedWhenContextMissing() {
    TenantContext.clear();

    assertThatThrownBy(() -> new TenantPortAdapter().currentTenantId())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("租户上下文缺失");
  }

  // ==== 辅助 ====

  private MockHttpServletRequest tokenRequest(long claimTenant, String forgedHeader) {
    String token = jwtTokenService.generateToken(1L, "alice", claimTenant, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/claims");
    request.addHeader("Authorization", "Bearer " + token);
    request.addHeader(TENANT_HEADER, forgedHeader);
    return request;
  }

  private HttpServletRequest runFilter(MockHttpServletRequest request) {
    AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
    FilterChain chain = (req, res) -> seen.set((HttpServletRequest) req);
    try {
      filter.doFilter(request, new MockHttpServletResponse(), chain);
    } catch (Exception e) {
      throw new IllegalStateException("过滤器执行失败", e);
    }
    return seen.get();
  }
}
