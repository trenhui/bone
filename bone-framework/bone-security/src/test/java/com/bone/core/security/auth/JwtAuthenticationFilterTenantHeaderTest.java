package com.bone.core.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtTokenService;
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
 * {@link AbstractJwtAuthenticationFilter} 租户头归一化的防伪契约测试。
 *
 * <p><b>锁住的安全不变量</b>：只要请求携带合法 token，下游读到的 {@code X-Tenant-Id} 必须等于 token 内 <strong>已签名</strong>的
 * {@code tenantId} claim——调用方无法通过改请求头把操作落到别的租户。
 *
 * <p>本测试刻意不依赖任何具体模块：被测行为在抽象类中，故以匿名子类实例化。若有人删掉归一化、或把它降级为 「在某个下游 filter 里 {@code
 * TenantContext.setTenantId} 覆盖」（会被更晚执行的消费者用原始头覆盖回去）， 第一个用例即会失败。
 *
 * <p>注意类名不能以 {@code Abstract} 开头——Surefire 按 {@code Abstract*.java} 排除，会静默不执行。
 */
class JwtAuthenticationFilterTenantHeaderTest {

  private static final String SECRET = "test-secret-key-32-bytes-minimum-length!!";
  private static final String TENANT_HEADER = "X-Tenant-Id";
  private static final long CLAIM_TENANT = 1001L;

  private JwtTokenService jwtTokenService;
  private AbstractJwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    // 过滤器对「已认证」的请求会跳过一次处理，而 SecurityContextHolder 是线程本地、
    // 生产环境由 Spring Security 的 SecurityContextHolderFilter 按请求清理。单测里必须自己清，
    // 否则前一个用例认证过的上下文会让后一个用例静默跳过归一化。
    SecurityContextHolder.clearContext();
    JwtConfig jwtConfig = new JwtConfig(mock(ConfigurableEnvironment.class));
    jwtConfig.setSecretKey(SECRET);
    jwtTokenService = new JwtTokenService(jwtConfig);
    filter = new AbstractJwtAuthenticationFilter(jwtTokenService, jwtConfig) {};
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void forgedTenantHeaderIsOverriddenBySignedClaim() {
    HttpServletRequest seen = runFilter(tokenRequest(CLAIM_TENANT, "9999"));

    assertThat(seen.getHeader(TENANT_HEADER)).isEqualTo(String.valueOf(CLAIM_TENANT));
    assertThat(seen.getHeaders(TENANT_HEADER).nextElement())
        .isEqualTo(String.valueOf(CLAIM_TENANT));
  }

  @Test
  void gatewayPathIsUnchangedWhenHeaderAlreadyMatchesClaim() {
    // 网关 JwtAuthGlobalFilter 已用同一 claim 覆盖过该头，此处应无行为变化
    HttpServletRequest seen = runFilter(tokenRequest(CLAIM_TENANT, String.valueOf(CLAIM_TENANT)));

    assertThat(seen.getHeader(TENANT_HEADER)).isEqualTo(String.valueOf(CLAIM_TENANT));
  }

  @Test
  void platformTenantClaimOverridesForgedHeader() {
    // 平台租户 0 是最常见的 claim，同样不得放行伪造头
    HttpServletRequest seen = runFilter(tokenRequest(0L, "9999"));

    assertThat(seen.getHeader(TENANT_HEADER)).isEqualTo("0");
  }

  @Test
  void requestWithoutTokenKeepsHeaderUntouched() {
    // 无 token（内部调用 / 本地演示）不参与归一化，行为与改动前一致
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
    request.addHeader(TENANT_HEADER, "9999");

    assertThat(runFilter(request).getHeader(TENANT_HEADER)).isEqualTo("9999");
  }

  @Test
  void malformedTokenIsIgnoredAndHeaderUntouched() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
    request.addHeader("Authorization", "Bearer not-a-jwt");
    request.addHeader(TENANT_HEADER, "9999");

    assertThat(runFilter(request).getHeader(TENANT_HEADER)).isEqualTo("9999");
  }

  // ==== 平台管理员租户切换（X-Acting-Tenant-Id，ADR-0029 平台全局视图入口）====

  @Test
  void platformAdminActingHeaderSwitchesEffectiveTenant() {
    HttpServletRequest seen =
        runFilter(
            platformTokenRequest(
                List.of("iam:tenants:read"), String.valueOf(CLAIM_TENANT), "1002"));

    assertThat(seen.getHeader(TENANT_HEADER)).isEqualTo("1002");
    assertThat(seen.getHeader(AbstractJwtAuthenticationFilter.ACTING_TENANT_HEADER))
        .isEqualTo("1002");
  }

  @Test
  void platformTokenWithoutSwitchScopeCannotAct() {
    // 平台 token 但无 iam:tenants:read（平台普通账号）：切换头被忽略，回落平台租户 0
    HttpServletRequest seen = runFilter(platformTokenRequest(List.of(), "0", "1002"));

    assertThat(seen.getHeader(TENANT_HEADER)).isEqualTo("0");
  }

  @Test
  void tenantTokenWithActingHeaderIsRejected() {
    // 租户 token 即便伪造切换头（甚至带切换权限码），也强制归一化为自身 claim——防伪不变量不被削弱
    String token =
        jwtTokenService.generateToken(1L, "alice", CLAIM_TENANT, List.of("iam:tenants:read"));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
    request.addHeader("Authorization", "Bearer " + token);
    request.addHeader(TENANT_HEADER, String.valueOf(CLAIM_TENANT));
    request.addHeader(AbstractJwtAuthenticationFilter.ACTING_TENANT_HEADER, "1002");

    assertThat(runFilter(request).getHeader(TENANT_HEADER)).isEqualTo(String.valueOf(CLAIM_TENANT));
  }

  @Test
  void invalidActingValuesAreIgnored() {
    for (String bad : new String[] {"abc", "0", "-5"}) {
      HttpServletRequest seen =
          runFilter(platformTokenRequest(List.of("iam:tenants:read"), "0", bad));
      assertThat(seen.getHeader(TENANT_HEADER)).as("acting=%s", bad).isEqualTo("0");
    }
  }

  /** 带 scopes 的平台/租户 token 请求：tenantIdClaim 为生效身份租户，actingHeader 为切换头取值。 */
  private MockHttpServletRequest platformTokenRequest(
      List<String> scopes, String forgedTenantHeader, String actingHeader) {
    String token = jwtTokenService.generateToken(1L, "alice", 0L, scopes);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
    request.addHeader("Authorization", "Bearer " + token);
    request.addHeader(TENANT_HEADER, forgedTenantHeader);
    request.addHeader(AbstractJwtAuthenticationFilter.ACTING_TENANT_HEADER, actingHeader);
    return request;
  }

  private MockHttpServletRequest tokenRequest(long claimTenant, String forgedHeader) {
    String token = jwtTokenService.generateToken(1L, "alice", claimTenant, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
    request.addHeader("Authorization", "Bearer " + token);
    request.addHeader(TENANT_HEADER, forgedHeader);
    return request;
  }

  /** 跑一遍过滤器并返回下游实际收到的请求。 */
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
