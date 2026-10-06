package com.bone.studio.generator.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtTokenService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 锁定「生成器必须接受平台 JWT」这一修复。
 *
 * <p><b>为什么需要这条测试</b>：修复前 {@code SecurityConfig} 只有 {@code httpBasic()}，从不校验 {@code Authorization:
 * Bearer <JWT>}，而网关与前端统一发 Bearer token⇒ 生成器全部端点在真实链路上恒 401 （{@code
 * AUTH_UNAUTHORIZED}）。此缺陷<b>不会被任何已有测试发现</b>：既有契约测试都用 {@code withBasicAuth()}，恰好走的是唯一被支持的那条分支 ——
 * 即"测试全绿但线上不可用"的典型形态。
 *
 * <p><b>判据设计</b>：{@link #bearerTokenIsAccepted()} 与 {@link #requestWithoutTokenIsRejected()}
 * 构成一对。前者防"过滤器缺失"，后者防"过滤器把所有人都放行"（无脑permitAll 也会让前者绿）。
 * 另有一条<b>密钥一致性</b>判据：生成器读的密钥必须与签发方（bone-iam）同源，否则验签必失败。
 */
@SpringBootTest
class JwtAuthenticationFilterTest {

  @Autowired private WebApplicationContext context;

  @Autowired private JwtTokenService jwtTokenService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    SecurityContextHolder.clearContext();
  }

  private String token() {
    return jwtTokenService.generateToken(1L, "admin", 0L, List.of("generator:*:read"));
  }

  @Test
  @DisplayName("带合法 Bearer token 的请求必须被放行（修复前恒 401）")
  void bearerTokenIsAccepted() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/generator/data-sources")
                .param("page", "1")
                .param("size", "5")
                .header("Authorization", "Bearer " + token())
                .header("X-Tenant-Id", "0"))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  @DisplayName("无 token 必须仍被拒（防「过滤器放行所有人」的反向退化）")
  void requestWithoutTokenIsRejected() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/generator/data-sources")
                .param("page", "1")
                .param("size", "5")
                .header("X-Tenant-Id", "0"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("签名错误的 token 必须被拒（防「只看 Bearer 前缀就放行」）")
  void forgedTokenIsRejected() throws Exception {
    String forged = token().substring(0, token().lastIndexOf('.') + 1) + "ZmFrZXNpZ25hdHVyZQ";
    mockMvc
        .perform(
            get("/api/v1/generator/data-sources")
                .param("page", "1")
                .param("size", "5")
                .header("Authorization", "Bearer " + forged)
                .header("X-Tenant-Id", "0"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("生成器与签发方共用同一密钥配置（bone.iam.jwt.secret-key 缺失 ⇒ 落到 change-me 默认值）")
  void secretKeyIsSharedWithIssuer() {
    // application.yml 里 bone.iam.jwt.secret-key 必须显式声明：SDK 的 JwtConfig 默认值是
    // "change-me-change-me-..."，与 bone-iam 的签发密钥不一致 ⇒ 验签必失败（表现为一切请求 401）。
    String configured = context.getEnvironment().getProperty("bone.iam.jwt.secret-key");
    assertThat(configured).isNotNull().isNotBlank().doesNotStartWith("change-me");
  }

  @Test
  @DisplayName("过滤器依赖的 JwtConfig / JwtTokenService 可从容器注入（防 JwtConfig 未被扫描的回归）")
  void jwtBeansAreAvailable() {
    // 修复过程中真实踩过：JwtConfig 无自动配置，主类 @ComponentScan 漏掉
    // "com.bone.core.security.jwt" ⇒ 启动期报 "required a bean of type JwtTokenService that could not
    // be found"。
    // 这里的注入本身就是那条判据。
    assertThat(jwtTokenService).isNotNull();
    assertThat(context.getBean(JwtConfig.class).getSecretKey())
        .isNotBlank()
        .doesNotStartWith("change-me");
  }
}
