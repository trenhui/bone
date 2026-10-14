package com.bone.metadata.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link JwtAuthenticationFilter} 单元测试——IAM token 的 claim 形状（{@code scopes}）与旧形状（{@code roles}）双读。
 *
 * <p><b>为什么有 {@code scopesOnlyToken_doesNotThrow}</b>（2026-10-13 必崩缺陷回归）：IAM 签发侧（{@code
 * bone-security/JwtTokenService}）只写 {@code scopes} claim；本过滤器原先只读 {@code roles} 且无 null 保护，{@code
 * claims.get("roles")} 返 null 后 {@code roles.stream()} 抛 NPE——NPE 不在 {@code catch (JwtException)}
 * 内，直接击穿过滤器链打成 500。生产 {@code security.enabled=true} 时「带 IAM JWT 调本服务」必崩。本用例锁死：scopes 形状 token
 * 必须正常建立认证且链继续。
 */
class JwtAuthenticationFilterTest {

  private final JwtUtil jwtUtil = new JwtUtil();
  private final JwtAuthenticationFilter filter;

  JwtAuthenticationFilterTest() {
    jwtUtil.init(); // @PostConstruct 手动触发（随机密钥，签发/验签同实例即可）
    filter = new JwtAuthenticationFilter(jwtUtil);
  }

  @BeforeEach
  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  /** 签发一个只带 scopes 的 token（IAM 现行形状：subject + userId + tenantId + scopes）。 */
  private String scopesOnlyToken(List<String> scopes) {
    return jwtUtil.generateToken(
        "admin",
        Map.of(
            "userId", "764059967975063552",
            "tenantId", "0",
            "scopes", scopes));
  }

  /** 签发一个只带 roles 的 token（旧形状 / 测试桩形状）。 */
  private String rolesOnlyToken(List<String> roles) {
    return jwtUtil.generateToken("legacy", Map.of("roles", roles));
  }

  /** 执行过滤器并返回「链是否继续」（500 崩溃时链不会继续）。 */
  private boolean runFilter(String token, AtomicBoolean reached) throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/v1/runtime/entities/x/records");
    if (token != null) {
      request.addHeader("Authorization", "Bearer " + token);
    }
    FilterChain chain =
        (req, res) -> {
          reached.set(true);
        };
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    return reached.get();
  }

  @Test
  void scopesOnlyToken_doesNotThrow_andAuthoritiesSet() throws Exception {
    // 核心回归：修复前 roles.stream() NPE → 500；现 scopes 兜底建立 authorities
    List<String> scopes = List.of("metadata:runtime:read", "runtime:*:read", "platform.admin");
    AtomicBoolean reached = new AtomicBoolean(false);
    assertTrue(runFilter(scopesOnlyToken(scopes), reached), "链必须继续（NPE 会在此中断）");
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertNotNull(auth, "scopes 形状 token 必须建立认证");
    assertEquals("admin", auth.getName());
    assertEquals(3, auth.getAuthorities().size(), "authorities 必须来自 scopes claim");
    assertTrue(
        auth.getAuthorities().contains(new SimpleGrantedAuthority("platform.admin")),
        "guard 判据码（platform.admin）必须出现在 authorities 中");
    assertEquals("764059967975063552", auth.getDetails(), "userId claim 进 details（审计链）");
  }

  @Test
  void rolesOnlyToken_backwardCompatible() throws Exception {
    AtomicBoolean reached = new AtomicBoolean(false);
    assertTrue(runFilter(rolesOnlyToken(List.of("metadata:read")), reached));
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertNotNull(auth, "roles 形状 token 仍可用（向后兼容）");
    assertEquals(1, auth.getAuthorities().size());
    assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("metadata:read")));
  }

  @Test
  void rolesTakePrecedenceOverScopes() throws Exception {
    String token =
        jwtUtil.generateToken(
            "dual", Map.of("roles", List.of("a:code"), "scopes", List.of("b:code")));
    AtomicBoolean reached = new AtomicBoolean(false);
    runFilter(token, reached);
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertEquals(1, auth.getAuthorities().size());
    assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("a:code")), "roles 优先");
  }

  @Test
  void neitherRolesNorScopes_emptyAuthorities_notThrow() throws Exception {
    String token = jwtUtil.generateToken("bare", Map.of("tenantId", "0"));
    AtomicBoolean reached = new AtomicBoolean(false);
    assertTrue(runFilter(token, reached), "双缺不得抛（修复前 roles null → NPE）");
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertNotNull(auth, "认证主体仍建立（用户名可信），权限集为空");
    assertTrue(auth.getAuthorities().isEmpty());
  }

  @Test
  void invalidToken_noAuthentication_chainContinues() throws Exception {
    AtomicBoolean reached = new AtomicBoolean(false);
    // 签名密钥相同但内容篡改：jjwt 解析抛 JwtException → catch → 不清上下文（本就无认证）
    String tampered = scopesOnlyToken(List.of("x")) + "tampered";
    assertTrue(runFilter(tampered, reached), "JwtException 必须被 catch，链继续");
    assertNull(SecurityContextHolder.getContext().getAuthentication(), "解析失败的 token 不得留下认证");
  }

  @Test
  void noAuthorizationHeader_noAuthentication() throws Exception {
    AtomicBoolean reached = new AtomicBoolean(false);
    assertTrue(runFilter(null, reached), "无 Authorization 头时过滤器直通");
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void malformedAuthorizationHeader_ignored() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
    request.addHeader("Authorization", "Basic dXNlcjpwYXNz"); // 非 Bearer 形状
    AtomicBoolean reached = new AtomicBoolean(false);
    FilterChain chain = (req, res) -> reached.set(true);
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    assertTrue(reached.get());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void generateToken_isDeterministicShape_forProbe() {
    // 签发/解析闭环自证：JwtUtil 随机密钥实例内自签自解析必须成功（防密钥装配回归）
    String token = scopesOnlyToken(List.of("metadata:read"));
    var claims = jwtUtil.parseToken(token);
    assertEquals("admin", claims.getSubject());
    List<?> scopes = claims.get("scopes", List.class);
    assertEquals(1, scopes.size());
  }
}
